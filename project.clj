(defproject quanta "0.1.0-SNAPSHOT"
  :description "Distributed sparse integer vectors."
  :url "https://github.com/maxcountryman/quanta"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[clout "2.1.2"]
                 [com.cognitect/transit-clj "0.8.247"]
                 [factual/clj-leveldb "0.1.1"]
                 [org.clojure/clojure "1.8.0-alpha2"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/tools.logging "0.3.0"]
                 [ring/ring-jetty-adapter "1.3.2"]
                 [ring/ring-json "0.3.1"]
                 [criterium "0.4.3"]]
  :global-vars {*warn-on-reflection* true}
  :jvm-opts ^:replace ["-server"]
  :profiles {:uberjar {:aot :all}}
  :test-selectors {:default (complement :benchmark)
                   :benchmark :benchmark}
  :main quanta.core)
