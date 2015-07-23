(ns quanta.handler
  "Message handling logic."
  (:require [clojure.string        :as string]
            [quanta.database       :as database]
            [quanta.message        :as message]
            [quanta.util           :as util]))

;;
;; Message sending.
;;

(defn timestamp [] (quot (System/currentTimeMillis) (* 1000 6)))

(defn addr->peer-key [addr] (str "n:" addr))

(defn peer-key->addr [peer-key] (apply str (drop 2 peer-key)))

(defn rand-peer
  "Returns a random peer address, excluding this node."
  [{:keys [node-addr peers]}]
  (some-> @peers
          (dissoc (addr->peer-key node-addr))
          keys
          rand-nth
          peer-key->addr))

(defn send-heartbeat
  "Sends a heartbeat message to a peer."
  [{:keys [socket]} peer addr]
  (->> (message/new (addr->peer-key addr) {0 (timestamp)} 0)
       (message/send socket peer)))

(defn send-messages
  "Sends messages via the given socket to the given address. Messages are
  assumed to be a sequence of messages formatted via message/new."
  [socket addr messages]
  (pmap (partial message/send socket addr) messages))

(defn send-responses
  "Sends forwards and responses, when they exist, to a random peer and the
  sender respectively. The first argument should be a node map and the second
  argument should be a message map containing a list of forwards and
  responses."
  [{:keys [socket] :as node} {:keys [addr forwards responses]}]
  ;; Send forward any new values and heartbeat, acknowledging the sender's
  ;; liveness to the cluster.
  ;;
  ;; TODO: Possibly this should forward to N + 1 / 2 peers, where N is the
  ;;       number of known peers.
  (when-let [peer (rand-peer node)]
    (when (seq forwards)
      (send-heartbeat socket peer addr)
      (send-messages socket peer forwards)))

  ;; Send back any updated values.
  (when (seq responses)
    (send-messages socket addr responses)))

;;
;; Message handling.
;;

(defn heartbeat?
  "Returns true if a key is formatted as a heartbeat otherwise false."
  [k]
  (.startsWith ^String k "n:"))

(defn get-store
  "Returns either the peer list store or the key store, depending on message
  type."
  [node k]
  (if (heartbeat? k)
    (:peers node)
    (:store node)))

(defn forward!
  "Returns a message intended to be forwarded onto a peer or if there are no
  relevant changes nil. This function may mutate the datastore!"
  [store k v ttl]
  (let [updates (database/update-vector! store k v)]
    (when (and (seq updates) (> ttl 0))
      (message/new k updates (dec ttl)))))

(defn response
  "Returns a message intended to be sent back to the sending peer or if there
  are no relevant changes nil."
  [store k v ttl]
  (when (> ttl 0)
    (let [updates (database/max-vector store k v)]
      (when (seq updates)
        (message/new k updates (dec ttl))))))

(defn message-type
  "Returns either :aggregate, :search, or :default."
  [_ {:keys [k]}]
  (condp #(.contains ^String %2 %1) k
    "*" :aggregate
    "%" :search
    :default))

(defn update-peer-list!
  [{:keys [peers] :as node} {:keys [addr] :as msg}]
  (when addr
    (database/update-vector! peers (addr->peer-key addr) {0 (timestamp)}))
  msg)

(defmulti handle-message message-type)

(defmethod handle-message :default
  [node {:keys [addr k v ttl]}]
  (let [store (get-store node k)]
    {:addr      addr
     :forwards  (keep #(forward! store % v ttl) [k])
     :responses (keep #(response store % v ttl) [k])}))

(defmethod handle-message :search
  [node {:keys [addr k v ttl]}]
  (let [store (get-store node k)
        ks    (database/match store k)
        msgs  (map #(-> (message/new % v ttl)
                        (assoc :addr addr)) ks)]
    (apply (partial merge-with conj)
           (map #(handle-message node %) msgs))))

(defmethod handle-message :aggregate
  [node {:keys [addr k v ttl]}]
  (let [store (get-store node k)
        agg   (->> (database/match store k)
                   (map #(database/get store %))
                   (remove empty?)
                   (apply merge-with max))]

    ;; Ensure we store the aggregate of the local keys in the database prior to
    ;; processing the incoming message.
    (database/put! store k agg)

    {:addr      addr
     :forwards  (keep #(forward! store % v ttl) [k])
     :responses (keep #(response store % v ttl) [k])}))
