(ns quanta.test_database
  (:require [clojure.test :refer [deftest is]]
            [quanta.database :as database]))

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
  (let [db (database/leveldb-store ".test-primary.db" ".test-trigram.db")]
    (database/put! db "foobar" "a")
    (database/put! db "foobaz" "b")
    (database/put! db "nada" "c")
    (is (= (database/match db "missing%") ()))
    (is (= (database/match db "foob%") '("foobar" "foobaz")))
    (is (= (database/match db "fo%") '("foobar" "foobaz")))
    (is (= (database/match db "nada%") '("nada")))))
