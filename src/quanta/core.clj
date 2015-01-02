(ns quanta.core
  "Quanta is a distributed CRDT of keys and values.

  Keys may be associated with values, which are sparse vectors of integers.
  These vectors may be updated only via element-wise max. This constraint
  yields a CRDT property because the max operation is idempotent, commutative,
  and associative.

  This quality makes Quanta useful as a kind of sketch database. For example,
  HyperLogLog can be implemented with the above constraints. Other possible
  uses include bloom filters, vector clocks, and min-hashes."
  (:require [clojure.string        :as string]
            [clojure.tools.cli     :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [quanta.database       :as database]
            [quanta.handler        :as handler]
            [quanta.node           :as node]
            [quanta.udp            :as udp]
            [quanta.util           :as util])
  (:gen-class))

(def specs
  [["-a"
    "--node-addr NODEADDR"
    "Address to bind UDP socket to"
    :assoc-fn (fn [m k v]
                (let [[host port] (util/parse-addr v)]
                  ;; Increment the HTTP server port by 100.
                  (-> m
                      (update-in [:http-addr] (constantly
                                                (str host ":" (+ port 100))))
                      (assoc k v))))
    :default "localhost:3000"]
   ["-H"
    "--http-addr HTTPADDR"
    "Address to bind HTTP server to"
    :default "localhost:3100"]
   ["-p"
    "--peers PEERS"
    "Addresses of known peers"
    :parse-fn #(string/split % #" ")]
    ["-h"
    "--help"
    "Print this help"
    :default false]])

(defn peer-map
  [peers]
  (into {} (map #(vector
                   (str "n:" %)
                   {0 (handler/timestamp)})
                peers)))

(defn -main
  [& args]
  (let [{:keys [summary] {:keys [help http-addr node-addr peers]} :options}
        (parse-opts args specs)]
    (when help
      (println summary))

    (when-not help
      (let [peers (peer-map peers)]
        (log/info "Starting quanta node on" node-addr)
        (log/info "Starting HTTP server on" http-addr)
        (-> (node/new node-addr http-addr peers) node/start)))))
