(ns quanta.test_handler
  (:require [clojure.test   :refer [deftest is]]
            [quanta.handler :as handler]))

(deftest test-addr->peer-key
  (is (= (handler/addr->peer-key "localhost:3000")
         "n:localhost:3000")))

(deftest test-peer-key->addr
  (is (= (handler/peer-key->addr "n:localhost:3000")
         "localhost:3000")))

(deftest test-rand-peer
  (let [node {:node-addr "localhost:3000"
              :peers (atom {"n:localhost:3000" {}
                            "n:localhost:3001" {}
                            "n:localhost:3002" {}})}]

    ;; Ensure the node's address is excluded.
    (is (every? nil? (repeatedly 100 #(some #{(:node-addr node)}
                                            (handler/rand-peer node)))))))

(deftest test-heartbeat?
  (is (true? (handler/heartbeat? "n:foo")))
  (is (false? (handler/heartbeat? "foo"))))
