(ns formic.robots
  (:require [org.httpkit.client :as http]
            [clojurewerkz.urly.core :as urly]
            [clj-robots.core :as robots]))

(def ^:private robot-files
  "Mapping from domain to clj-robots parsed robots.txt info"
  (atom {}))

(defn ensure
  [domain]
  ;; Last one wins. This won't happen often, so the waste is minimal.
  (when (nil? (get @robot-files domain))
    (swap! robot-files domain
           (robots/parse
            (:body
             ;; Asymptotically 0 blocking calls... Evil?
             @(http/get (str "http://"
                             domain
                             "/robots.txt")))))))

(defn crawlable? [user-agent url]
  (let [domain (urly/host-of url)]
          (ensure domain)
          (robots/crawlable? (get @robot-files domain)
                             (urly/path-of url)
                             :user-agent user-agent)))

(defn crawl-delay [user-agent url default]
  (let [domain (urly/host-of url)]
    (ensure domain)
    (or (:crawl-delay (get @robot-files domain)) default)))

(defn meta-index?
  [user-agent page]
  ;; TODO: do.
  true)

(defn meta-follow? [user-agent page]
  ;; TODO: do.
  true)
