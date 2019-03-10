(defproject clojure-zulip "0.1.0-SNAPSHOT"
  :description "A Clojure client for the Zulip API"
  :url "https://github.com/tthieman/zulip-clojure"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.logging "0.4.1"]
                 [org.clojure/core.async "0.4.490"]
                 [clj-http "3.9.1"]
                 [cheshire "5.8.1"]])
