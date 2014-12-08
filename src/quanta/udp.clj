(ns quanta.udp
  "Basic User Datagram Protocol utilities."
  (:refer-clojure :exclude [send])
  (:import [java.net DatagramPacket DatagramSocket InetAddress]))

(def ^{:const true} MAX-BUFFER-SIZE 1500)

(defn socket
  "Returns a DatagramSocket either unbound or otherwise bound to the given
  host and port."
  ([]
   (DatagramSocket.))
  ([host port]
   (let [address (InetAddress/getByName host)]
     (DatagramSocket. port address))))

(defn- string->inet-address ^InetAddress
  [host]
  (InetAddress/getByName host))

(defn send
  "Sends a byte array via the provided socket to the given host and port."
  [^DatagramSocket s host ^Integer port ^bytes bs]
  (let [buffer-size (count bs)
        address     (string->inet-address host)
        packet      (DatagramPacket. bs buffer-size address port)]
    (.send s packet)))

(defn receive
  "Returns a DatagramPacket via the provided socket. When larger than
  buffer-size, the data will be truncated."
  [^DatagramSocket s buffer-size]
  (let [buffer  (byte-array buffer-size)
        packet  (DatagramPacket. buffer buffer-size)]
    (.receive s packet)
    packet))

(defn packet->string
  "Returns the String coercion of the given DatagramPacket's getData value."
  [^DatagramPacket packet]
  (String. (.getData packet)))

(comment
  (with-open [s (socket "localhost" 3000)]
    (let [r (future (packet->string (receive s 1024)))]
      (send s "localhost" 3000 (.getBytes "Hello, UDP world!"))
      @r)))
