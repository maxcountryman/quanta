(ns quanta.test_message
  (:require [clojure.test :refer [deftest is]]
            [quanta.message :as message]
            [quanta.udp     :as udp]
            [quanta.util    :as util]))

(deftest test-encoding-decoding
  (is (instance? (Class/forName "[B") (message/encode "")))
  (is (= (-> {:foo :bar} message/encode message/decode) {:foo :bar})))

(deftest test-received-sent
  ;; For now use a real socket, but this should probably be stubbed out in the
  ;; future.
  (let [addr   "localhost:6060"
        socket (apply udp/socket (util/parse-addr addr))
        msg    (future (message/receive socket))
        expect (message/new "foo" "bar" 0)]
    ;; 1. Send a message to the socket.
    (message/send socket addr expect)

    ;; 2. Ensure the sent message is received by the future.
    (is (= @msg (assoc expect :addr addr)))))
