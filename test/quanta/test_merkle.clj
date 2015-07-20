(ns quanta.test_merkle
  (:require [criterium.core :as cr]
            [clojure.test :refer [are deftest is]]
            [quanta.merkle :as merkle]))

(deftest test-node=
  (let [a (merkle/->Node 0 0 0 nil nil)
        b (merkle/->Node 1 1 1 nil nil)]
    (is (true? (merkle/node= a a)))
    (is (false? (merkle/node= a b)))))

(deftest test-key-range
  (let [n (merkle/->Node 0 :a :c nil nil)]
    (is (= (merkle/key-range n) (list :a :c)))))

(deftest test-children
  (let [a (merkle/->Node 0 0 0 nil nil)
        b (merkle/->Node 0 0 0 a nil)
        c (merkle/->Node 0 0 0 nil a)
        d (merkle/->Node 0 0 0 a a)]
    (are [x y z] (= (merkle/children x) (list y z))
         a nil nil
         b a nil
         c nil a
         d a a)))

(deftest test-leaf?
  (let [a (merkle/->Node 0 0 0 nil nil)
        b (merkle/->Node 0 0 0 a nil)
        c (merkle/->Node 0 0 0 nil a)
        d (merkle/->Node 0 0 0 a a)]
    (is (true? (merkle/leaf? a)))
    (are [n] (false? (merkle/leaf? n)) b c d)))

(deftest test-join-nodes
  (let [a (merkle/->Node 0 :a :a nil nil)
        b (merkle/->Node 1 :b :b nil nil)]
    (is (= (merkle/->Node 802035103 :a :b a b)
           (merkle/join-nodes a b)))))

(deftest test-tree
  ;; Depiction of our test tree:
  ;;
  ;;           o :a :c
  ;;          / \
  ;;   :a :b o   o :c :c
  ;;        / \
  ;; :a :a o   o :b :b
  (let [a (merkle/tree [[:a 1] [:b 2] [:c 3]])
        b (merkle/->Node -1585813068 :a :c
                        (merkle/->Node -1721426635 :a :b
                                       (merkle/->Node 1392991556 :a :a nil nil)
                                       (merkle/->Node -971005196 :b :b nil nil))
                        (merkle/->Node -1556392013 :c :c nil nil))]
    (is (= a b))))

(deftest ^:benchmark test-tree-perf
  (let [kvs (take 1e5 (iterate (fn [[x y]] [(inc x) (inc y)]) [0 0]))]
    (cr/with-progress-reporting (cr/bench (merkle/tree kvs)))))
