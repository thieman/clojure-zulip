(ns clojure-zulip.client
  (:require [clojure.tools.logging :refer [error]]
            [clj-http.client :as http]
            [clojure.core.async :as async]
            [cheshire.core :as cheshire]))

(defn- uri
  "Construct a URI from route fragments."
  [& more]
  (->> (map #(if (= (last "/") (last %)) % (str % "/")) more)
       (apply str)
       (butlast)
       (apply str)))

(defn extract-body
  "Return the body from an HTTP response, deserialized from JSON if
  applicable."
  [response]
  (if-not (= (get-in response [:headers "content-type"]) "application/json")
    (:body response)
    (cheshire/parse-string (:body response) true)))

(defn- request-opts
  "Return dict of processed arguments for use by request."
  [verb connection]
  {:connection-opts (:opts connection)
   :http-fn (case verb
              :GET http/get
              :POST http/post
              :PATCH http/patch)
   :arg-symbol (case verb
                 :GET :query-params
                 :POST :form-params
                 :PATCH :query-params)})

(defn request
  "Issue a request to the Zulip API. Accepted verbs are :GET, :POST,
  and :PATCH. Return a channel to which the response body will be
  written.
  TODO: For some reason, Zulip's SSL cert doesn't get along with
  clj-http, requiring the :insecure? true flag. This is obviously
  Really Bad, so fix it."
  ([verb connection endpoint request-args]
     (let [{:keys [connection-opts http-fn arg-symbol]} (request-opts verb connection)
           channel (async/chan)]
       (future
         (try
           (let [result (http-fn (uri (:base-url connection-opts) endpoint)
                                 {:basic-auth [(:username connection-opts)
                                               (:api-key connection-opts)]
                                  :insecure? true
                                  arg-symbol request-args})]
             (async/>!! channel (extract-body result)))
           (catch Exception e
             (error {:ms (System/currentTimeMillis)
                     :method verb
                     :uri (uri (:base-url connection-opts) endpoint)
                     :request-args request-args
                     :exception e})
             (async/>!! channel {}))))
       channel)))
