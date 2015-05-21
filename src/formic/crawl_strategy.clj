(ns formic.crawl-strategy
  (:require [clojure.core.async :as async :refer [>! <! put! go chan go-loop]]
            [clojurewerkz.urly.core :as urly]

            [formic.robots :refer [crawlable? crawl-delay]]
            [formic.url-filter :refer [crawl?]]
            ))

(defprotocol CrawlStrategy
  (enqueue [this ch url] "Here's a URL, put it on ch if you want to crawl it.")
  (stop! [this] "Shut down."))

;; This is a start. I'm not sure if we need to monitor the size of the
;; queues. We should at least log them for debugging / parameter
;; fiddling / optimisation
(defn get-crawler [{:keys [user-agent queue-length default-crawl-delay
                           robot-policy url-filter]}]
  (let [input-ch (atom nil)
        alive? (ref true)
        counts (atom {})
        queues (ref {})]
    (reify
      CrawlStrategy
      (enqueue [this out-ch url]
        (let [domain (urly/host-of url)]
          (if-let [q (get @queues domain)]
            (do
              (put! q url))
            (dosync
             (when @alive?
               (if (nil? (get @queues domain))
                 (let [q (chan (async/dropping-buffer queue-length))
                       ;;TODO: buf of 1 implies just in time filtering?
                       fq (chan 1 (comp (filter
                                         (partial crawlable?
                                                  robot-policy user-agent))
                                        (filter (partial crawl? url-filter))))
                       crawl-delay (* 1000
                                      (max (crawl-delay
                                            robot-policy domain user-agent)
                                           default-crawl-delay))]
                   (alter queues assoc domain q)
                   (async/pipe q fq)
                   (go-loop []
                     (when-let [v (<! fq)]
                       (>! out-ch v)
                       (<! (async/timeout crawl-delay))
                       (recur))))
                 (enqueue this out-ch url)))))))
      
      (stop! [_]
        (dosync
         (ref-set alive? false)
         (doseq [q (vals @queues)]
           (async/close! q)))))))
