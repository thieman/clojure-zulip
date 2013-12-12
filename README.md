# clojure-zulip

An asynchronous Clojure client for the Zulip API.

## Getting Started

### Connections

You'll usually want to define one connection for each Zulip bot you're controlling and pass that to every call you make. The `connection` function only returns an options dict and does not immediately open any connections to Zulip.

```clojure
(require '[clojure-zulip.core :as zulip])
(def conn (zulip/connection {:username "my username" :api-key "my key"}))
```

### Basic Commands

Every API command returns a `core.async` channel to which the HTTP response will be published. For each request, a new future is created to make the request, publish the response to the channel, and then terminate. Connection pooling is currently not implemented, so if you are making a ton of concurrent requests, you may need to create a pool yourself.

A `synchronous` wrapper macro is also provided to make any request synchronous.

```clojure
(def channel (zulip/subscriptions conn))
(async/<!! channel)
=> {:msg "", :result "success", :subscriptions []}

(zulip/synchronous (zulip/subscriptions conn))
=> {:msg "", :result "success", :subscriptions []}
```

Functions are provided for the commands listed on the [Zulip endpoints page](zulip.com/api/endpoints) as well as some undocumented commands such as those used for managing subscriptions.

### Subscribing to Events

A common pattern in bot design is to subscribe to a list of streams and then respond to any messages received on those streams or through private messages. The `subscribe-events` function is provided to make this easier.

```clojure
(def queue-id (:queue-id (zulip/synchronous (zulip/register conn))))
(def events-channel (zulip/subscribe-events conn queue-id))
(loop [] (async/<!! events-channel)) ;; any messages are published to this channel
```

## License

Copyright © 2013 Travis Thieman

Distributed under the Eclipse Public License version 1.0.
