(ns quanta.node
  "Node logic."
  (:require [clojure.tools.logging :as log]
            [clojure.string        :as string]
            [quanta.database       :as database]
            [quanta.message        :as message]
            [quanta.udp            :as udp]
            [quanta.util           :as util])
  (:import [java.net DatagramSocket InetSocketAddress]))

;;
;; Utilities.
;;

(defn peer-key->addr
  "Converts a peer key string to an address string."
  [peer-key]
  (->> (string/split peer-key #":")
       (drop 1)
       (string/join ":")))

(defn addr->peer-key
  "Converts an address string to a peer key string."
  [addr]
  (str "n:" addr))

(defn rand-peer-addr
  "Returns a random peer address, excluding this node's address and the sending
  node's address."
  [{:keys [addr peers]} msg-addr]
  (let [node-key (addr->peer-key addr)
        peer-key (addr->peer-key msg-addr)]

    ;; TODO -- Should filter out nodes which we haven't seen in a while here.
    (some-> @peers
            (dissoc node-key peer-key)
            keys
            rand-nth
            peer-key->addr)))

(defn time-stamp
  "Time since epoch, rounded to the nearest minute."
  []
  (quot (System/currentTimeMillis)
        (* 1000 6)))

(defn heartbeat?
  "Returns true if the message's key is prefixed with n:, otherwise false."
  [{:keys [k]}]
  (.startsWith ^String k "n:"))

(defn heartbeat
  "Given a message, constructs a heartbeat message using the sender's address."
  [{:keys [addr]}]
  (-> (message/new (str "n:" addr) {0 (time-stamp)} 1)
      (assoc :addr addr)))

(defn this?
  "Returns true if the message is a heartbeat whose key matches this node's
  address, otherwise false."
  [{:keys [addr]} {:keys [k]}]
  (= (addr->peer-key addr) k))

(defn send-message
  "Given a node map, an address string, a key, a value, and optionally a TTL,
  sends a new message to addr. Note that if no TTL is provided, this will
  default to the number of peers a node knows about plus one divided by two."
  ([{:keys [peers] :as node} addr k v]
   (let [ttl (-> @peers count inc (quot 2))]  ;; N + 1 / 2.
     (send-message node addr k v ttl)))
  ([{:keys [socket]} addr k v ttl]
   (->> (message/new k v ttl)
        (message/send socket addr))))

;;
;; Message handling.
;;

;; Messages are processed by type, where the types are a default, a search,
;; and an aggregate query. Each type is handled separately, however where
;; possible the codepaths are shared, e.g. when handling a search query, each
;; returned message is handled by the default query codepath--aggregates are an
;; exception to this rule.
;;
;; When a message is routed, we check the TTL and assuming it's larger than
;; zero, proceed. Also a heartbeat for the message sender is processed at this
;; point. These gossip messages are forwarded to a random node from this node's
;; peer list with the exclusion of the current node.
;;
;; For default and search queries, where a sent vector's indices are smaller
;; than or missing from the node's persisted vectors, we generate an update
;; vector which we send back to the sender, decrementing the TTL. Where a sent
;; vector's indices are larger than or missing from the node's persisted
;; vectors, we generate an update vector which we sernd forward to a random
;; node, excluding the sender.

(defn message-type
  "Returns either :aggregate, :search, or :default."
  [_ {:keys [k]}]
  (condp #(.contains ^String %2 %1) k
    "*" :aggregate
    "%" :search
    :default))

(defmulti handler message-type)

(defn handle-default
  [node store {:keys [addr k v ttl] :as msg}]
  (when (> ttl 0)
    ;; Send any diffs on the remote's vector back to the message sender.
    (let [updates (database/max-vector store k v)]
      (when (seq updates)
        (send-message node addr k updates (dec ttl)))))

  ;; Send any updates on the local vector forward to a random peer.
  ;;
  ;; Note that we do this before checking the TTL to persist any updates which
  ;; we won't otherwise forward on to other peers.
  (let [updates (database/update-vector! store k v)]
    (when (and (seq updates) (> ttl 0))
      (when-let [peer-addr (rand-peer-addr node addr)]
        (send-message node peer-addr k updates (dec ttl))))))

(defmethod handler :default
  [{:keys [peers store] :as node} msg]
  (let [store (if (heartbeat? msg) peers store)]
    ;; Only process a message when it's not a heartbeat for the current node.
    ;; The idea here is to filter out messages about ourselves, since they are
    ;; unnecessary.
    (when-not (this? node msg)
      (handle-default node store (heartbeat msg))
      (handle-default node store msg))))

(defn handle-search
  [node store {:keys [addr k v ttl]}]
  (when (> ttl 0)
    (doseq [match (database/match store k)]
      (handle-default node store (-> (message/new match v ttl)
                                     (assoc :addr addr))))))

(defmethod handler :search
  [{:keys [peers store] :as node} msg]
  (let [store (if (heartbeat? msg) peers store)]
    ;; Only process a message when it's not a heartbeat for the current node.
    ;; The idea here is to filter out messages about ourselves, since they are
    ;; unnecessary.
    (when-not (this? node msg)
      (handle-default node store (heartbeat msg))
      (handle-search node store msg))))

(defn handle-aggregate
  [node store {:keys [addr k v ttl]}]
  (when (> ttl 0)
    (let [agg (->> (database/match store k)
                   (map (partial database/get store))
                   (remove empty?)
                   (apply merge-with max))]
      (send-message node addr k agg (dec ttl)))))

(defmethod handler :aggregate
  [{:keys [peers store] :as node} msg]
  (let [store (if (heartbeat? msg) peers store)]
    (when-not (this? node msg)
      (handle-default node store (heartbeat msg))
      (handle-aggregate node store msg))))

;;
;; Node lifecycle.
;;

(defn new
  "Creates a new node map with a main loop function. This may be passed to the
  start function to setup necessary runtime processes and execute the main
  loop. Likewise it may also be passed to the stop function to reverse this."
  [addr peers]
  {:addr  addr
   :peers (database/memory-store (atom peers))
   :main  #(future
             (try (while true
                    (handler % (message/receive (:socket %))))
                  (catch Exception e
                    (log/error e))))})

(defn start
  "Given a node map, starts all the processes needed and adds them to the map
  if the node has not already been started. Otherwise returns the provided map
  unaltered. Calling this function is idempotent."
  [{:keys [addr thread main] :as node}]
  (if thread
    node
    (let [[host port] (util/parse-addr addr)
          node (assoc node
                      :socket (udp/socket host port)
                      :store  (database/leveldb-store
                                (format ".quanta-primary-%s-%d" host port)
                                (format ".quanta-trigram-%s-%d" host port)))]
      ;; Bootstrap peer list by requesting a random peer's peer list. Note that
      ;; this triggers additional traffic since each received message will in
      ;; turn generate heartbeat messages to other random peers.
      (when-let [peer-addr (rand-peer-addr node addr)]
        (send-message node peer-addr "n:%" {}))

      (assoc node :thread (main node)))))

(defn stop
  "Given a node map, stops all the active proccesses and removes them from the
  map if the node has been started. Otherwise returns the provided map
  unaltered. Calling this function is idempotent."
  [{:keys [socket store thread] :as node}]
  (if thread
    (do (future-cancel thread)
        (.close store)
        (.close socket)
        (dissoc node :thread :socket :store))
    node))

(def ^{:doc "Stops then starts a given node map."}
  restart
  (comp start stop))
