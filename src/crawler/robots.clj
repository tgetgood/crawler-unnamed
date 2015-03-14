(ns crawler.robots
  (:require [org.httpkit.client :as http]
            [clojurewerkz.urly.core :as urly]
            [clj-robots.core :as robots]))

(defprotocol RobotsPolicy
  "Abstraction of robots.txt interpretation for a given domain and
  user-agent."
  (crawlable? [this user-agent url])
  (crawl-delay [this domain user-agent]))

(defn clj-robots-filter
  [& opts]
  (let [txts (atom {})
        ensure (fn [domain]
                 ;; Race condition! Worst case we fetch the robots
                 ;; file a few times in parallel and the last swap!
                 ;; wins. Given that doesn't matter and the files get
                 ;; fetched so seldom, do I care?
                 ;;
                 ;; Using a ref instead makes this go away, but adds
                 ;; the overhead of a dosync on every read. And there
                 ;; will be a lot of reads...
                 (when (nil? (get @txts domain))
                   (swap! txts domain
                          (robots/parse
                           (:body
                            ;; Asymptotically 0 blocking calls... Evil?
                            @(http/get (str "http://"
                                            domain
                                            "/robots.txt")))))))]
    (reify
      RobotsPolicy
      (crawlable? [_ user-agent url]
        (let [domain (urly/host-of url)]
          (ensure domain)
          (robots/crawlable? (get @txts domain)
                             (urly/path-of url)
                             :user-agent user-agent)))
      (crawl-delay [_ domain user-agent]
        (ensure domain)
        ;; clj-robots doesn't parse crawl-delay by user-agent
        (or (:crawl-delay (get @txts domain)) 0)))))


