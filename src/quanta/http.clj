(ns quanta.http
  "HTTP interface."
  (:require [clout.core           :refer [route-matches]]
            [ring.adapter.jetty   :refer [run-jetty]]
            [ring.middleware.json :refer [wrap-json-body
                                          wrap-json-response]]
            [quanta.handler       :as handler]))

(defn json?
  [{:keys [content-type]}]
  (boolean
    (when content-type
      (re-find #"^application/(.+\+)?json" content-type))))

(defn ensure-json
  [request]
  (when-not (json? request)
    {:status 400 :body "Non-JSON Content-Type"}))

(defn ensure-key
  [{{:strs [k]} :body}]
  (when-not k {:status 400 :body "Missing key"}))

(defn ensure-value
  [{{:strs [v]} :body}]
  (when-not v {:status 400 :body "Missing value"}))

;; TODO: Stricter evaluation of vector, e.g. need to ensure indices are longs!
(defn ensure-vector
  [{{:strs [v]} :body}]
  (when-not (map? v)
    {:status 400 :body "Malformed vector"}))

(defn keys->longs
  "Converts the keys of a given map from strings to longs."
  [m]
  (into {}
    (for [[k v] m]
      [(java.lang.Long/parseLong k) v])))

(defmulti response :request-method)

(defmethod response :put
  [{:keys [node]
    {:keys [socket node-addr]} :node
    {:strs [k v ttl]} :body :as request}]
  (let [some-errors (some-fn ensure-json
                             ensure-key
                             ensure-value
                             ensure-vector)]
    (or (some-errors request)
        (let [msg {:k k :v (keys->longs v) :ttl (or ttl 1)}
              {:keys [forwards responses]} (handler/handle-message node msg)]
          (when-let [peer (handler/rand-peer node)]
            (when (seq forwards)
              (handler/send-messages socket peer forwards)))
          (if (every? #(= msg %) responses)
            {:status 201 :body responses}
            {:status 200 :body responses})))))

(defmethod response :default
  [_]
  {:status 405 :body "Method Not Allowed"})

;;
;; HTTP Routes.
;;

(defn wrap-routes
  "Middleware which provides various HTTP endpoints. In particular this exposes
  a health endpoint, a key retrieval endpoint, and a key setting endpoint. An
  HTTP client may be used to access these endpoints."
  [handler {:keys [peers] :as node}]
  (fn [request]
    (condp route-matches request
      "/health" {:status 200
                 :body   {:status :okay :peers @peers}}
      "/"       (-> request
                    (assoc :node node)
                    response)
      (handler request))))

(defn new
  [node host port]
  (-> (constantly {:status 404 :body "Not Found"})
      (wrap-routes node)
      (wrap-json-body {:bigdecimal? true})
      (wrap-json-response {:pretty true})
      (run-jetty {:host host :port port :join? false})))
