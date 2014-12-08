(ns quanta.core
  (:require [clojure.tools.cli     :refer [cli]]
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
    "--addr"
    "Address to bind UDP socket to"
    :default "localhost:3000"]
   ["-p"
    "--peers"
    "Addresses of a known peers"]
    ["-h"
    "--help"
    "Print this help"
    :default false
    :flag true]])

(defn -main
  [& args]
  (let [[opts args banner] (apply cli args specs)]
    (when (:help opts)
      (println banner))

    (when-not (:help opts)
      (let [addr        (:addr opts)
            peers       (into {} (map #(vector
                                         (str "n:" %)
                                         {0 (node/time-stamp)})
                                      [(:peers opts)]))
            [host port] (util/parse-addr addr)]

        (log/info "Starting quanta node on" addr)

        (let [socket (udp/socket host port)
              store  (database/leveldb-store
                       (format PRIMARY-PATH-FORMAT host port)
                       (format TRIGRAM-PATH-FORMAT host port))
              node   (node/new socket store peers)]
          (node/start node))))))
