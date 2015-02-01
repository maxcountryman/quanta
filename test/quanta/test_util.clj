(ns quanta.test_util
  (:require [clojure.test :refer [deftest is]]
            [quanta.util :as util]))

(deftest test-parse-int
  (is (= (util/parse-int "42") 42)))

(deftest test-parse-addr
  (is (= (util/parse-addr "localhost:3333")
         ["localhost" 3333])))
