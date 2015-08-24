(ns quanta.test_node
  (:require [clojure.test :refer [deftest is]]
            [quanta.message :as message]
            [quanta.node :as node]))

(deftest test-node
  (let [peers-a {"n:localhost:4344" {0 0}}
        peers-b {"n:localhost:4343" {0 0}}
        a (node/new "localhost:4343" "localhost:4353" peers-a)
        b (node/new "localhost:4344" "localhost:4354" peers-b)]

    (is (= (:node-addr a) "localhost:4343"))
    (is (= (:node-addr b) "localhost:4344"))

    ;; Start the test nodes.
    (doseq [n [a b]]
      (node/start n))

    (is (some @(:peers a) (keys peers-a)))
    (is (some @(:peers b) (keys peers-b)))

    ;; Stop test nodes.
    (doseq [n [a b]]
      (node/stop n))))
