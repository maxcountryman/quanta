(defproject quanta "0.1.0-SNAPSHOT"
  :description "Distributed sparse integer vectors."
  :url "https://github.com/maxcountryman/quanta"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[com.cognitect/transit-clj "0.8.247"]
                 [com.stuartsierra/component "0.2.1"]
                 [factual/clj-leveldb "0.1.1"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/tools.logging "0.3.0"]]
  :global-vars {*warn-on-reflection* true}
  :jvm-opts ^:replace ["-server"]
  :main quanta.core)
