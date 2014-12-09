(ns quanta.util
  "Utility functions."
  (:require [clojure.string :as string]))

(defn parse-int [^String s] (Integer. s))

(defn parse-addr
  [addr]
  (let [[host port] (string/split addr #":")]
    (list host (parse-int port))))
