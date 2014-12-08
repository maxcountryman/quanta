(ns quanta.database
  (:refer-clojure :exclude [get])
  (:require [clj-leveldb                :as level]
            [clojure.core               :as core]
            [clojure.data               :refer [diff]]
            [clojure.tools.logging      :as log]
            [clojure.set                :refer [difference union]]
            [clojure.string             :as string]
            [quanta.message             :as message])
  (:import [java.io Closeable]))

(declare trigram-keys query-trigrams)

(defn k->re-s
  [k]
  [(re-pattern (-> k
                   (string/replace "." "[.]")
                   (string/replace "%" ".*")))
   (string/replace k "%" "")])

;; Store abstraction, borrowed from Tom Crayford's liza library. Thanks, Tom!
(defprotocol Store
  (get [s k])
  (match [s substring])
  (put! [s k v]))

(deftype MemoryStore [a]
  Store
  (get [_ k]
    (core/get @a k))

  (match [_ substring]
    (let [[re s] (k->re-s substring)]
      (reduce-kv (fn [matches k v]
                   (if (re-matches re k)
                     (conj matches k)
                     matches))
                 ()
                 @a)))

  (put! [_ k v]
    (swap! a assoc k v)
    v)

  clojure.lang.IDeref
  (deref [_]
    @a))

(defn memory-store [a] (MemoryStore. a))

(deftype LevelDBStore [db tindex]
  Store
  (get [_ k]
    (level/get db k))

  (match [_ substring]
    (let [[re s] (k->re-s substring)]
      (query-trigrams tindex s re)))

  (put! [_ k v]
    ;; As we insert a new key into the primary store, we also update the
    ;; trigram index store.
    (let [trigrams (trigram-keys k)]
      (doseq [trigram trigrams]
        (level/put tindex trigram (if-let [existing (level/get tindex trigram)]
                                    (union existing #{k})
                                    #{k}))))
    (level/put db k v)
    v))

(defn create-db
  [path]
  (level/create-db path {:key-decoder byte-streams/to-string
                         :val-encoder message/encode
                         :val-decoder message/decode}))

(defn leveldb-store
  [ppath tpath]
  (LevelDBStore. (create-db ppath) (create-db tpath)))

(defn put-with-merge!
  "Puts a value into the store but first checks to see if the key already
  exists. When it does, updates that existing value with the provided merge
  function."
  [store k v merge-fn]
  (put! store k (if-let [existing (get store k)]
                  (merge-fn existing v)
                  v)))

(defn element-wise-max
  "Returns a map where stored values which are larger than or do not appear in
  the provided map are returned."
  [store k v]
  (when-let [existing (get store k)]
    (if (empty? v)
      existing
      (merge (reduce-kv (fn [updates i n]
                          (if-let [m (core/get existing i)]
                            (if (> m n)
                              (assoc updates i m)
                              updates)))
                        {}
                        v)
             (first (diff existing v))))))

(defn update!
  "Updates the db where provided values are larger than local values and
  returns a map of the same data."
  [store k v]
  (if-let [existing (get store k)]
    (reduce-kv (fn [updates i n]
                 (let [m (core/get existing i 0)]
                   (if (> n m)
                     (do (put-with-merge! store k {i n} merge)
                         (assoc updates i n))
                     updates)))
               {}
               v)
    (put! store k v)))

;; Trigram indexing.
;;
;; To achieve wildcard queries, a virtual trigram index is used. This index is
;; built by splitting keys into trigrams and storing those trigrams as keys.
;; These contain sets of keys in the primary store. For example, the key
;; "foobar" becomes ("foo" "oob" "oba" "bar"), each of these being a key in the
;; trigram index containing the set #{"foobar"}.

(defn n-grams [n s] (partition n 1 s))

(defn trigram-keys
  "Returns a lazy sequence of trigrams of the given key."
  [k]
  (map (partial apply str) (n-grams 3 k)))

(defn query-trigrams
  "Queries a virtual index for a given substring, returning any keys which
  match."
  [db s re]
  (when-let [ks (if (> (count s) 2)
                  ;; Find exact trigrams, retrieve their keys' sets.
                  (reduce (fn [acc trigram]
                            (if-let [ks (level/get db trigram)]
                              (conj acc ks)
                              acc))
                          ()
                          (trigram-keys s))

                  ;; Traverse trigrams, searching for any containing s,
                  ;; retrieve their keys' sets.
                  (reduce (fn [acc [trigram ks]]
                            (if (.contains ^String trigram s)
                              (conj acc ks)
                              acc))
                          ()
                          (level/iterator db)))]
    (->> ks
         (apply union)
         (filter #(re-matches re %)))))
