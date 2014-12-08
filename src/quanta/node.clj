(ns quanta.node
  (:require [clojure.stacktrace]
            [clojure.string        :as string]
            [clojure.tools.logging :as log]
            [quanta.database       :as database]
            [quanta.message        :as message]
            [quanta.util           :as util])
  (:import [java.net DatagramSocket InetSocketAddress]))

(defn socket-address->peer-key
  [^InetSocketAddress sa]
  (str "n:" (.getHostName sa) ":" (.getPort sa)))

(defn peer-key->addr
  [peer-key]
  (->> (string/split peer-key #":")
       (drop 1)
       (string/join ":")))

(defn rand-peer-addr
  "Returns a random peer address, excluding this node's address."
  [{:keys [^DatagramSocket socket peers]}]
  (let [sa       (.getLocalSocketAddress socket)
        this-key (socket-address->peer-key sa)]
    ;; TODO -- Should filter out nodes which we haven't seen in a while here.
    (some-> (dissoc @peers this-key) keys rand-nth peer-key->addr)))

(defn time-stamp
  "Time since epoch, rounded to the nearest minute."
  []
  (quot (System/currentTimeMillis) 60000))

(defn heartbeat?
  "Returns true if the message's key matches a known heartbeat pattern,
  otherwise false."
  [{:keys [k]}]
  (.startsWith ^String k "n:"))

(defn heartbeat
  "Returns a new heartbeat message."
  [{:keys [addr]}]
  (-> (message/new (str "n:" addr) {0 (time-stamp)} 1)
      (assoc :addr addr)))

(defn send-message
  ([{:keys [socket peers] :as node} addr k v]
   ;; Time-To-Live is by default calculated as N + 1 / 2 where N is the number
   ;; of known peers. This is 0 when there are no peers.
   (let [ttl (-> @peers count inc (quot 2))]
     (send-message node addr k v ttl)))
  ([{:keys [socket]} addr k v ttl]
   (->> (message/new k v ttl)
        (message/send socket addr))))

(defn message-type
  "Returns either :aggregate, :search, or :default."
  [_ {:keys [k]}]
  (condp #(.contains ^String %2 %1) k
    "*" :aggregate
    "%" :search
    :default))

(defmulti handler message-type)

(defmethod handler :aggregate
  [node msg]
  ;; TODO -- Aggregated searches.
  (prn :aggregate node msg))

(defn handle-search
  [node store {:keys [addr k v ttl]}]
  (when (> ttl 0)
    (doseq [match (database/match store k)]
      (handler node (-> (message/new match v ttl)
                        (assoc :addr addr))))))

(defmethod handler :search
  [{:keys [peers store] :as node} msg]
  (let [store (if (heartbeat? msg) peers store)]
    ;; (handler node (heartbeat msg))
    (handle-search node store msg)))

(defn handle-default
  [node store {:keys [addr k v ttl]}]
  (when (> ttl 0)
    ;; Send any diffs on the remote's vector back to the message sender.
    (let [updates (database/element-wise-max store k v)]
      (when (seq updates)
        (send-message node addr k updates (dec ttl)))))

  ;; Send any updates on the local vector forward to a random peer.
  (let [updates (database/update! store k v)]
    (when (and (seq updates) (> ttl 0))
      (when-let [peer-addr (rand-peer-addr node)]
        (send-message node peer-addr k updates (dec ttl))))))

(defmethod handler :default
  [{:keys [peers store] :as node} msg]
  (let [store (if (heartbeat? msg) peers store)]
    (handle-default node store (heartbeat msg))
    (handle-default node store msg)))

(defn new
  [^DatagramSocket socket store peers]
  {:main     #(future (while @(:started? %)
                        (handler % (message/receive socket))))
   :started? (atom false)
   :peers    (database/memory-store (atom (or peers {})))
   :store    store
   :socket   socket})

(defn start
  [{:keys [main started?] :as node}]
  (swap! started? (constantly true))

  ;; TODO -- Announce node to cluster.
  (try
    ;; TODO -- Deref'ing blocks, we don't want that but we also want to report
    ;; errors when they happen in the future's thread.
    @(main node)
    (catch Exception e
      (log/error e (clojure.stacktrace/print-stack-trace e)))))

(defn stop
  [{:keys [started?]}]
  (swap! started? (constantly false)))
