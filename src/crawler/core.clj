(ns crawler.core
  (:require [org.httpkit.client :as http]
            [clojure.core.async :as async :refer [>! <! put! go chan go-loop]]
            [clj-robots.core :as robots]

            [crawler.robots :refer [crawlable? crawl-delay clj-robots-filter]]
            [crawler.links :refer [extract-followable-links]]
            ))

(defn crawler
  [{:keys [seed-urls
           default-crawl-delay
           domain-filter-fn
           link-extractor-fn
           queue-size
           user-agent
           handler] :or {:default-crawl-delay 1
                         :link-extractor-fn get-all-links
                         }}])

(defprotocol URLFilter
  (filter-url [this url] "Returns true if this URL should be crawled.")
  (record-url [this response] "Registers response for future use."))

(defn one-pass-archiver
  [domain crawl-queue & [{:keys [queue-length] :or {queue-length 1}}]]
  (let [queue (chan (async/dropping-buffer queue-length))
        len (atom 0)]))




(defn fetch [url res-ch]
  (http/get url (fn [x] (put! res-ch x) (async/close! res-ch))))


(defn crawl-url? [url])

(def par 1)

(def crawl-queue (chan 10000))
(def fetched-queue (chan 1000))

(def mf (async/mult fetched-queue))

(def link-ch (chan 100))
(def handler-ch (chan 100))
(def feedback-ch (chan 100))

(async/tap mf link-ch)
(async/tap mf handler-ch)
(async/tap mf feedback-ch)


;; (async/pipeline par crawl-queue #(filter filter-url (get-links (:body %))) link-ch)

(defprotocol IDomainQueue
  (enqueue [this url] [this domain url]))

;; This is a start. The main problem I forsee is that it kills the
;; queues as they empty which may lead to a race condition where the
;; crawler exits while there's still work in the pipeline.
(defn enqueue-url [out-fn user-agent qsize default-crawl-delay]
  (let [queues (ref {})]
    (reify
      IDomainQueue
      (enqueue [this url]
        (enqueue this (#_get-domain url) url))
      (enqueue [this domain url]
        (if-let [q (get @queues domain)]
          (put! q url)
          (dosync
           (if (nil? (get @queues domain))
             (let [q (chan (async/dropping-buffer qsize))
                   crawl-delay (* 1000
                                  (max (clj-robots-filter domain user-agent)
                                       default-crawl-delay))]
               (alter queues assoc domain q)
               (go-loop []
                 (if-let [v (<! q)]
                   (do
                     (out-fn v)
                     (<! (async/timeout crawl-delay))
                     (recur))
                   (dosync
                    (alter queues dissoc domain)
                    (when (empty? @queues)
                      (out-fn nil))))))
             (enqueue this domain url))))))))

;; (defn init-crawler [seeds]
;;   (let [crawl-queue (chan 1000)
;;         fetch-queue (chan 1000)
;;         fetch-dist (async/mult fetch-queue)]
;;     (map (partial create-domain-queue crawl-queue) (domains seeds))
;;     (async/pipeline-async par fetched-queue fetch crawl-queue)
;;     (start-link-processor fetch-dist crawl-queue)
;;     ))
