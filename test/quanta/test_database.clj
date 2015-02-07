(ns quanta.test_database
  (:require [clj-leveldb     :as level]
            [clojure.set     :refer [union]]
            [clojure.test    :refer [deftest is use-fixtures]]
            [quanta.database :as database]))

(def ^{:private true} db (atom nil))

(defn db-fixture
  [test-fn]
  (let [paths [".test-primary.db" ".test-trigram.db"]]
    ;; Setup the test db instance.
    (with-open [store (apply database/leveldb-store paths)]
      (swap! db (constantly store))
      (test-fn))

    ;; Destroy the test db instance.
    (doseq [path paths]
      (level/destroy-db path))))

(use-fixtures :each db-fixture)

(deftest test-n-grams
  (is (= (database/n-grams 1 "foobar")
         '((\f) (\o) (\o) (\b) (\a) (\r))))
  (is (= (database/n-grams 3 "foobar")
         '((\f \o \o) (\o \o \b) (\o \b \a) (\b \a \r)))))

(deftest test-trigram-keys
  (is (= (database/trigram-keys "foobar") '("foo" "oob" "oba" "bar"))))

(deftest test-k->re-s
  ;; Note that we apply str to the regex pattern in order to check equality.
  ;; This is because Java's regex pattern objects do not implement equals.
  ;;
  ;; See: http://dev.clojure.org/jira/browse/CLJ-1182
  (is (= (update-in (database/k->re-s "foo.") [0] str)
         [(str #"foo[.]") "foo."]))
  (is (= (update-in (database/k->re-s "foo%") [0] str)
         [(str #"foo.*") "foo"]))
  (is (= (update-in (database/k->re-s "foo*") [0] str)
         [(str #"foo.*") "foo"])))

(deftest test-match
  (database/put! @db "fooqux" "a")
  (database/put! @db "foobar" "b")
  (database/put! @db "foobaz" "c")
  (database/put! @db "nada" "d")
  (is (= (database/match @db "missing%") ()))
  (is (= (database/match @db "foob%") '("foobar" "foobaz")))
  (is (= (database/match @db "fo%") '("foobar" "fooqux" "foobaz")))
  (is (= (database/match @db "nada%") '("nada"))))

(deftest test-put-with-merge!
  (database/put! @db "foo" #{1 2 3})
  (database/put-with-merge! @db "foo" #{1 5 3} union)
  (= (database/get @db "foo") #{1 2 3 5}))

(deftest test-max-vector
  (database/put! @db "vector" {0 1 13 42})
  (is (= (database/max-vector @db "missing" {}) nil))
  (is (= (database/max-vector @db "vector" {}) {0 1 13 42}))
  (is (= (database/max-vector @db "vector" {0 0 13 42}) {0 1}))
  (is (= (database/max-vector @db "vector" {0 1 13 42}) {})))

(deftest test-update-vector!
  ;; Ensure inserting a fresh vector populates the db.
  (is (= (database/update-vector! @db "vector" {0 1 13 42}) {0 1 13 42}))
  (is (= (database/get @db "vector") {0 1 13 42}))

  ;; Ensure only updates are returned.
  (is (database/update-vector! @db "vector" {0 0 13 42}) {0 1})
  (is (= (database/get @db "vector") {0 1 13 42}))

  ;; Ensure partial updates work correctly.
  (is (= (database/update-vector! @db "vector" {0 2}) {0 2}))
  (is (= (database/get @db "vector") {0 2 13 42}))
  
  ;; Ensure an empty vector does not change stored vector.
  (is (= (database/update-vector! @db "vector" {}) {}))
  (is (= (database/get @db "vector") {0 2 13 42}))
  
  ;; Ensure adding new indices updates the store vector.
  (is (= (database/update-vector! @db "vector" {3 7}) {3 7}))
  (is (= (database/get @db "vector") {0 2 3 7 13 42})))
