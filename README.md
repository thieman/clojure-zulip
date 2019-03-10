# clojure-zulip

An asynchronous Clojure client for the Zulip API.

## Getting Started

```clojure
[clojure-zulip "0.1.0-SNAPSHOT"]
```

### Connections

You'll usually want to define one connection for each Zulip bot you're controlling and pass that to every call you make. The `connection` function only returns an options dict and does not immediately open any connections to Zulip.

```clojure
(require '[clojure-zulip.core :as zulip])
(def conn (zulip/connection {:username "my username" :api-key "my key"}))
```

### Basic Commands

Every API command returns a `core.async` channel to which the HTTP response will be published. For each request, a new future is created to make the request, publish the response to the channel, and then terminate. Connection pooling is currently not implemented, so if you are making a ton of concurrent requests, you may need to create a pool yourself.

A `sync*` wrapper macro is also provided to make any request synchronous.

```clojure
(def channel (zulip/subscriptions conn))
(async/<!! channel)
=> {:msg "", :result "success", :subscriptions []}

(zulip/sync* (zulip/subscriptions conn))
=> {:msg "", :result "success", :subscriptions []}
```

Functions are provided for the commands listed on the [Zulip endpoints page](zulip.com/api/endpoints) as well as some undocumented commands such as those used for managing subscriptions.

### Subscribing to Events

A common pattern in bot design is to subscribe to a list of streams and then respond to any messages received on those streams or through private messages. The `subscribe-events` function is provided to make this easier.

```clojure
(def queue-id (:queue_id (zulip/synchronous (zulip/register conn))))
(def events-channel (zulip/subscribe-events conn queue-id))
(loop [] (println (async/<!! events-channel)) (recur)) ;; any messages are published to this channel
```

## Echo Bot example

The following implements a bot that replies to messages of the form "!echo message" with "message".
```clojure
(ns echo-bot.core
  (:require [clojure-zulip.core :as zulip]
            [clojure.string :as str]
            [clojure.core.async :as async]))

(def conn (zulip/connection
  {:username "echo-bot@zulip.com"
   :api-key "secret api key"
   :base-url "https://api.zulip.com/v1"}))

(defn handle-event
  "Check whether event contains a message starting with '!echo' if yes,
  reply (either in private or on stream) with the rest of the message."
  [conn event]
  (let [message (:message event)
        {stream :display_recipient
         message-type :type
         sender :sender_email
         :keys [:subject :content]} message]
    ;; the message is for us if it begins with `!echo`
    (if (and content (str/starts-with? content "!echo "))
      ;; if so, remove leading `!echo`
      (let [reply (subs content 6)]
        ;; Reply to private message in private
        (if (= message-type "private")
          (zulip/send-private-message conn sender reply)
          ;; otherwise post to stream
          (zulip/send-stream-message conn stream subject reply))))))

(defn mk-handler-channel
  "Create channel that calls `handle-event` on input with `conn`"
  [conn]
  (let [c (async/chan)]
    (async/go-loop []
      (handle-event conn (async/<! c))
      (recur))
    c))

;; Connect event input to handler channel
(let [register-response (zulip/sync* (zulip/register conn))
      event-channel (zulip/subscribe-events conn register-response)
      handler-channel (mk-handler-channel conn)]
  (async/pipe event-channel handler-channel))
```

## License

Copyright © 2013 Travis Thieman

Distributed under the Eclipse Public License version 1.0.
