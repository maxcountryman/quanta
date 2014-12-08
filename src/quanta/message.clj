(ns quanta.message
  (:require [clojure.tools.logging :as log]
            [cognitect.transit     :as transit]
            [quanta.udp            :as udp]
            [quanta.util           :as util])
  (:refer-clojure :exclude [send])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream InputStream]
           [java.net DatagramPacket InetSocketAddress]))

(def ^{:const true} TRANSIT-FORMAT :msgpack)

(defn encode
  "Writes message with MessagePack format into a ByteArrayOutputStream as
  transit data. Returns the output stream."
  [message]
  (let [out    (ByteArrayOutputStream.)
        writer (transit/writer out TRANSIT-FORMAT)]
    (transit/write writer message)
    (.toByteArray out)))

(defn decode
  "Reads a byte array with MessagePack format. Returns the read value."
  [bs]
  (when bs
    (let [in     (ByteArrayInputStream. bs)
          reader (transit/reader in TRANSIT-FORMAT)]
      (transit/read reader))))

(defn receive
  [socket]
  (let [^DatagramPacket packet (udp/receive socket udp/MAX-BUFFER-SIZE)]
    (try
      (let [addr (let [^InetSocketAddress sa (.getSocketAddress packet)]
                   (str (.getHostName sa) ":" (.getPort sa)))
            msg  (decode (.getData packet))]
        (log/info "Received message ->" msg)
        (assoc msg :addr addr))
      (catch Exception e
        (log/error e "Could not receive message")))))

(defn send
  [socket address msg]
  (try
    (let [[host port] (util/parse-addr address)
          bs          (encode msg)]
      (log/info "Sent message ->" msg "to" address)
      (udp/send socket host port bs))
    (catch Exception e
      (log/error e "Could not send message"))))

(defn new
  "Returns a new message, given a key, value, and TTL."
  [k v ttl]
  {:k k :v v :ttl ttl})
