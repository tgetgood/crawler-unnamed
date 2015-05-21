(ns formic.url-filter
  (:require [clojure.core.async :as async :refer [>! <! put! go chan go-loop]]

            [metrics.core :refer [new-registry]]
            [metrics.counters :refer [defcounter inc!]]))

(defprotocol URLFilter
  (crawl? [this url] "Returns true if this URL should be crawled.")
  (crawled! [this response] "Registers response for future use."))

(def reg (new-registry))
(defcounter reg pages-seen)

(defn one-pass-archiver
  "Simple filter, looks at each URL at most once. Good for making a
  one pass archive of a set of domains."
  ;; TODO: This could be made super memory efficient as a trie over
  ;; the url strings...
  [& opts]
  (let [seen (atom #{})
        res-ch (atom nil)]
    (reify
      URLFilter
      (crawl? [_ url]
        (not (contains? @seen url)))
      
      (crawled! [_ response]
        (swap! seen conj (:url response))
        (inc! pages-seen)))))
