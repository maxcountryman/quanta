(ns quanta.database
  "Persistence layer abstractions."
  (:refer-clojure :exclude [get])
  (:require [clj-leveldb                :as level]
            [clojure.core               :as core]
            [clojure.data               :refer [diff]]
            [clojure.tools.logging      :as log]
            [clojure.set                :refer [difference union]]
            [clojure.string             :as string]
            [quanta.message             :as message])
  (:import [clj_leveldb LevelDB]
           [java.io Closeable]))

;;
;; Trigram indexing.
;;

;; To achieve wildcard queries, a virtual trigram index is used. This index is
;; built by splitting keys into trigrams and storing those trigrams as keys.
;; These contain sets of keys in the primary store. For example, the key
;; "foobar" becomes ("foo" "oob" "oba" "bar"), each of these being a key in the
;; trigram index containing the set #{"foobar"}.

(defn n-grams
  "Given n (gram size) and a string with which to make n-grams from, returns a
  lazy sequence of n-grams."
  [n s]
  (partition n 1 s))

(defn trigram-keys
  "Returns a lazy sequence of trigrams of the given key."
  [k]
  (map (partial apply str) (n-grams 3 k)))

(defn query-trigrams
  "Queries the database by breaking the provided substring into trigrams and
  then retrieving those. After retrieval the provided regex pattern is applied
  to the result.

  In the event the provided substring is fewer than three characters (i.e. not
  a trigram), we iterate all the keys, keeping any that contain the substring.
  These are then filtered as above, using regex.

  Returns a list of matching keys. These keys reside in the primary database.
  Note that this implies the trigram index was populated in tandem with the
  primary."
  [db s re]
  (when-let [ks (if (> (count s) 2)
                  ;; Find exact trigrams, retrieve their keys' sets.
                  (reduce (fn [acc trigram]
                            (if-let [ks (level/get db trigram)]
                              (conj acc ks)
                              acc))
                          ()
                          (trigram-keys s))

                  ;; Traverse trigrams, searching for any containing the
                  ;; substring s, retrieve their keys' sets.
                  (reduce (fn [acc [trigram ks]]
                            (if (.contains ^String trigram s)
                              (conj acc ks)
                              acc))
                          ()
                          (level/iterator db)))]
    (->> ks
         (apply union)
         (filter #(re-matches re %)))))

;;
;; Store abstraction.
;;

(defn k->re-s
  "Returns a tuple where the first element is a regex pattern and the second
  is a substring."
  [k]
  (vector (re-pattern (-> k
                          (string/replace "." "[.]")
                          (string/replace "*" ".*")
                          (string/replace "%" ".*")))
          (-> k
              (string/replace "%" "")
              (string/replace "*" ""))))

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

(defn memory-store
  "Given an atom, returns a new MemoryStore."
  [a]
  (MemoryStore. a))

(deftype LevelDBStore [^LevelDB db ^LevelDB tindex]
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
    v)

  Closeable
  (close [_]
    (.close db)
    (.close tindex)))

(defn create-db
  "Given a path, returns a Closeable database object with preset key-decoder,
  val-encoder, val-decoder: byte-streams/to-string, message/encode,
  message/decode, respeactively."
  [path]
  (level/create-db path {:key-decoder byte-streams/to-string
                         :val-encoder message/encode
                         :val-decoder message/decode}))

(defn leveldb-store
  "Given a primary path and trigram path, returns a new LevelDBStore."
  [ppath tpath]
  (LevelDBStore. (create-db ppath) (create-db tpath)))

;;
;; Query functions.
;;

(defn put-with-merge!
  "Puts a value into the store but first checks to see if the key already
  exists. When it does, updates that existing value with the provided merge
  function."
  [store k v merge-fn]
  (put! store k (if-let [existing (get store k)]
                  (merge-fn existing v)
                  v)))

(defn max-vector
  "Returns a map of only indices which either do not exist or are larger than
  provided values."
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

(defn update-vector!
  "Updates the store where provided vector indices either do not exist or are
  larger than stored values. Returns a map of any updates."
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
