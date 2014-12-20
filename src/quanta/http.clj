(ns quanta.http
  "HTTP interface."
  (:require [clout.core         :refer [route-matches]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.json :refer [wrap-json-response]]))

(defn wrap-routes
  [handler {:keys [peers]}]
  (fn [request]
    (condp route-matches request
      "/health" {:status 200 :body {:status :okay
                                    :peers @peers}})))

(defn new
  [node host port]
  (-> (constantly {:status 404 :body "Not Found"})
      (wrap-routes node)
      wrap-json-response
      (run-jetty {:host host :port port :join? false})))
