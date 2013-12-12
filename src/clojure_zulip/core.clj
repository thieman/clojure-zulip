(ns clojure-zulip.core
  (:require [clojure.core.async :as async]
            [cheshire.core :as cheshire]
            [clojure-zulip.client :as client]))

;; connection management

(def default-connection-opts
  {:username nil
   :api-key nil
   :base-url "https://zulip.com/api/v1/"})

(defn connection [user-opts]
  "Create a new connection pool based on the given user-opts. Return a
  dict of the connection elements."
  (let [opts (merge default-connection-opts user-opts)]
    {:opts opts}))

(defn close!
  "Attempt to kill any request threads still running."
  ([] (shutdown-agents))
  ([conn] (close!)))

;; basic API commands, typically one per endpoint

(defn send-private-message
  "Send a private message to specified users. Returns a channel with
  the result."
  [conn users message]
  (client/request :POST conn "messages"
                  {:type "private"
                   :content message
                   :to (cheshire/generate-string users)}))

(defn send-stream-message
  "Send a message to a specific stream. Returns a channel with the
  result."
  [conn stream subject message]
  (client/request :POST conn "messages"
                  {:type "stream"
                   :content message
                   :subject subject
                   :to stream}))

(defn send-message
  "Dispatches to relevant send message function based on whether a
  stream (string) or list of users is given."
  [& more]
  (if (string? (second more))
    (apply send-stream-message more)
    (apply send-private-message more)))

(defn register
  "Register a queue to listen for certain event types. Returns a
  channel with :queue_id, :max_message_id, and :last_event_id keys."
  ([conn] (register conn ["message" "subscriptions" "realm_user" "pointer"]))
  ([conn event-types] (register conn event-types false))
  ([conn event-types apply-markdown]
     (client/request :POST conn "register"
                     {:event_types (cheshire/generate-string event-types)
                      :apply_markdown apply-markdown})))

(defn events
  "Get events from the specified queue occuring after
  last-event-id. Returns a channel."
  ([conn queue-id last-event-id] (events conn queue-id last-event-id true))
  ([conn queue-id last-event-id dont-block]
     (client/request :GET conn "events"
                     {:queue_id queue-id
                      :last_event_id last-event-id
                      :dont_block dont-block})))

(defn get-messages
  "TODO: Make this work. Getting a 404."
  [conn]
  (client/request :GET conn "messages/latest" {}))

(defn members
  "Get members of your entire organization. Returns a channel.
   TODO: Can this return for only one or more streams?"
  [conn]
  (client/request :GET conn "users"))

(defn subscriptions
  "Get a list of current subscriptions. Returns a channel."
  [conn]
  (client/request :GET conn "users/me/subscriptions" {}))

(defn add-subscriptions
  "Subscribe to the specified streams. Returns a channel."
  [conn streams]
  (let [stream-join-dicts (map #(hash-map "name" %) streams)]
    (client/request :POST conn "users/me/subscriptions"
                    {:subscriptions (cheshire/generate-string stream-join-dicts)})))

(defn remove-subscriptions
  "Unsubscribe from the specified streams. Returns a channel."
  [conn streams]
  (client/request :PATCH conn "users/me/subscriptions"
                  {:delete (cheshire/generate-string streams)}))

;; higher-level convenience functions built on top of basic API commands

(defn- subscribe-events*
  "Launch a goroutine that continuously publishes events on the
  publish-channel until an exception is encountered or the connection
  is closed."
  [conn queue-id last-event-id publish-channel]
  (async/go
   (loop [conn conn
          queue-id queue-id
          last-event-id last-event-id
          publish-channel publish-channel]
     (let [result (async/<! (events conn queue-id last-event-id true))]
       (when-not (:exception result)
         (let [events (seq (:events result))]
           (if events
             (do (doseq [event events]
                   (async/>! publish-channel event))
                 (recur conn queue-id (apply max (map :id events)) publish-channel))
             (recur conn queue-id last-event-id publish-channel))))))))

(defn subscribe-events
  "Continuously issue requests against the events endpoint, updating
  the last-event-id so that each event is only returned once. If an
  exception is returned by any request, this terminates. Returns a
  channel to which events will be published."
  ([conn queue-id last-event-id]
     (let [publish-channel (async/chan)]
       (subscribe-events* conn queue-id last-event-id publish-channel)
       publish-channel)))

;; utility functions

(defmacro synchronous
  "Wrapper macro to make a request synchronously."
  [& body]
  `(async/<!! (do ~@body)))
