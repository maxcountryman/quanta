(ns quanta.message
  "Message abstractions."
  (:require [clojure.tools.logging :as log]
            [cognitect.transit     :as transit]
            [quanta.udp            :as udp]
            [quanta.util           :as util])
  (:refer-clojure :exclude [send])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream InputStream]
           [java.net DatagramPacket InetSocketAddress]))

(def ^{:const true} TRANSIT-FORMAT :msgpack)

(defrecord Message [n k v ttl])

(defn encode
  "Writes message with MessagePack format into a ByteArrayOutputStream as
  transit data. Returns the output stream."
  [^Message msg]
  (let [out    (ByteArrayOutputStream.)
        handlers (transit/record-write-handlers Message)
        writer (transit/writer out TRANSIT-FORMAT {:handlers handlers})]
    (transit/write writer msg)
    (.toByteArray out)))

(defn decode
  "Reads a byte array with MessagePack format. Returns the read value."
  [bs]
  (when bs
    (let [in     (ByteArrayInputStream. bs)
          handlers (transit/record-read-handlers Message)
          reader (transit/reader in TRANSIT-FORMAT {:handlers handlers})]
      (transit/read reader))))

(defn receive
  "Reads a DatagramPacket off the provided socket. Logs the output otherwise
  the exception. Decodes the message, associating the sender's address onto the
  message map, then returns this map."
  [socket]
  (let [^DatagramPacket packet (udp/receive socket udp/MAX-BUFFER-SIZE)]
    (try
      (let [addr (let [^InetSocketAddress sa (.getSocketAddress packet)]
                   (str (.getHostName sa) ":" (.getPort sa)))
            msg  (-> (decode (.getData packet)) (assoc :addr addr))]
        (log/info "Received message ->" msg)
        msg)
      (catch Exception e
        (log/error e "Could not receive message")))))

(defn send
  "Given a socket, address, and a message, sends the message. The socket
  should be a DatagramSocket, e.g. as returned by udp/socket. The address
  should be a string in the format host:port. Finally the message should be a
  map, e.g. as returned by message/new. Logs the sent message, otherwise the
  exception."
  [socket address msg]
  (try
    (let [[host port] (util/parse-addr address)
          bs          (encode msg)]
      (udp/send socket host port bs)
      (log/info "Sent message ->" msg "to" address))
    (catch Exception e
      (log/error e "Could not send message"))))

(defn new
  "Give node metadata, a key, a value, and a time-to-live, returns a new
  Message record."
  [{:keys [http-addr]} k v ttl]
  (let [node-meta {:http-addr http-addr}]
    (Message. node-meta k v ttl)))
