(defproject clojure-zulip "0.1.0-SNAPSHOT"
  :description "A Clojure client for the Zulip API"
  :url "https://github.com/tthieman/zulip-clojure"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/core.async "0.1.262.0-151b23-alpha"]
                 [clj-http "0.7.8"]
                 [cheshire "5.2.0"]])
