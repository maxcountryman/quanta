(ns quanta.node
  "Node logic."
  (:require [clojure.tools.logging :as log]
            [clojure.string        :as string]
            [quanta.database       :as database]
            [quanta.handler        :as handler]
            [quanta.http           :as http]
            [quanta.message        :as message]
            [quanta.udp            :as udp]
            [quanta.util           :as util])
  (:import [java.net DatagramSocket InetSocketAddress]))

;;
;; Node lifecycle.
;;

(defn new
  "Creates a new node map with a main loop function. This may be passed to the
  start function to setup necessary runtime processes and execute the main
  loop. Likewise it may also be passed to the stop function to reverse this."
  [node-addr http-addr peers]
  {:node-addr node-addr
   :http-addr http-addr
   :peers     (database/memory-store (atom peers))
   :main      #(future
                 (try (while true
                        (->> (message/receive (:socket %))
                             (handler/update-peer-list! %)
                             (handler/handle-message %)
                             (handler/send-responses %)))
                      (catch Exception e
                        (log/error e))))})

(defn start
  "Given a node map, starts all the processes needed and adds them to the map
  if the node has not already been started. Otherwise returns the provided map
  unaltered. Calling this function is idempotent."
  [{:keys [http-addr main node-addr running] :as node}]
  (if running
    node
    (let [[host port] (util/parse-addr node-addr)
          socket (udp/socket host port)
          node (assoc node
                      :socket socket
                      :store  (database/leveldb-store
                                (format ".quanta-primary-%s-%d" host port)
                                (format ".quanta-trigram-%s-%d" host port)))]

      ;; Bootstrap peer list by requesting a random peer's peer list. Note that
      ;; this triggers additional traffic since each received message will in
      ;; turn generate heartbeat messages to other random peers.
      (when-let [peer (handler/rand-peer node)]
        (log/info "Bootstrapping peerlist")
        (message/send socket peer {:k "n:%" :v {} :ttl 1}))

      (-> node
          (assoc :running (main node))
          (assoc :server (apply (partial http/new node)
                                (util/parse-addr http-addr)))))))

(defn stop
  "Given a node map, stops all the active proccesses and removes them from the
  map if the node has been started. Otherwise returns the provided map
  unaltered. Calling this function is idempotent."
  [{:keys [running server socket store] :as node}]
  (if running
    (do (future-cancel running)
        (.close server)
        (.close store)
        (.close socket)
        (dissoc node :running :server :socket :store))
    node))

(def ^{:doc "Stops then starts a given node map."}
  restart
  (comp start stop))
