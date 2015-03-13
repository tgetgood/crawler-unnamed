(ns crawler.robots
  (:require [org.httpkit.client :as http]
            [clj-robots.core :as robots]))

(defprotocol RobotsTXT
  "Abstraction of robots.txt interpretation for a given domain and
  user-agent."
  (crawlable? [this path])
  (crawl-delay [this]))

(defn- clj-robots-filter* [domain user-agent]
  (let [rt (robots/parse
            (:body @(http/get (str "http://" domain "/robots.txt"))))]
    (reify
      RobotsTXT
      (crawlable? [_ path]
        (robots/crawlable? rt path :user-agent user-agent))
      (crawl-delay [_]
        ;; clj-robots doesn't parse crawl-delay by user-agent
        (:crawl-delay rt)))))

(def clj-robots-filter (memoize clj-robots-filter*))
