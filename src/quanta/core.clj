(ns quanta.core
  "Quanta is a distributed CRDT of keys and values.

  Keys may be associated with values, which are sparse vectors of integers.
  These vectors may be updated only via element-wise max. This constraint
  yields a CRDT property because the max operation is idempotent, commutative,
  and associative.

  What is a datatype with such contraints useful for? A data structure with the
  above property can be used to construct vector clocks, bloom filters, and 
  hyperloglog. So long as values may be represented as sparse vectors which
  grow monotonically."
  (:require [clojure.string        :as string]
            [clojure.tools.cli     :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [quanta.database       :as database]
            [quanta.node           :as node]
            [quanta.udp            :as udp]
            [quanta.util           :as util])
  (:gen-class))

;; Quanta uses separate databases to store primary keys and trigrams.
(def ^{:const true} PRIMARY-PATH-FORMAT ".quanta-primary-%s-%d")
(def ^{:const true} TRIGRAM-PATH-FORMAT ".quanta-trigram-%s-%d")

(def specs
  [["-a"
    "--addr ADDR"
    "Address to bind UDP socket to"
    :default "localhost:3000"]
   ["-p"
    "--peers PEERS"
    "Addresses of a known peers"
    :parse-fn #(string/split % #" ")]
    ["-h"
    "--help"
    "Print this help"
    :default false]])

(defn -main
  [& args]
  (let [{{:keys [addr help peers]} :options :as opts} (parse-opts args specs)]
    (when help
      (println (:summary opts)))

    (when-not help
      (let [peers       (into {} (map #(vector
                                         (str "n:" %)
                                         {0 (node/time-stamp)})
                                      peers))
            [host port] (util/parse-addr addr)]

        (log/info "Starting quanta node on" addr)

        (let [socket (udp/socket host port)
              store  (database/leveldb-store
                       (format PRIMARY-PATH-FORMAT host port)
                       (format TRIGRAM-PATH-FORMAT host port))
              node   (node/new socket store peers)]
          (node/start node))))))
