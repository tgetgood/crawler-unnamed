(ns formic.robots
  (:require [clj-robots.core :as robots]
            [clojurewerkz.urly.core :as urly]
            [org.httpkit.client :as http]))

(def ^:private robot-files
  "Mapping from domain to clj-robots parsed robots.txt info"
  (atom {}))

(defn get-robots
  [domain]
  ;; Last one wins. This won't happen often, so the waste is minimal.
  (when (nil? (get @robot-files domain))
    (swap! robot-files assoc domain
           (robots/parse
            (:body
             ;; TODO: Send the user-agent on this request as well
             ;; Asymptotically 0 blocking calls... Evil?
             @(http/get (str "http://"
                             domain
                             "/robots.txt"))))))
  (get @robot-files domain))

(defn crawlable? [user-agent url]
  (let [domain (urly/host-of url)]
          (robots/crawlable? (get-robots domain)
                             (urly/path-of url)
                             :user-agent user-agent)))

(defn crawl-delay [user-agent url default]
  (let [domain (urly/host-of url)]
    (or (:crawl-delay (get-robots domain)) default)))

(defn meta-index?
  [user-agent page]
  ;; TODO: do.
  true)

(defn meta-follow? [user-agent page]
  ;; TODO: do.
  true)
