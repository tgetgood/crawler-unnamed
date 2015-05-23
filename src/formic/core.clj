(ns formic.core
  (:require [clojure.core.async :as async :refer [<! >! chan go go-loop put!]]
            [formic
             [robots :as robots]
             [url-filter :refer [crawl?]]
             [utils :refer [fetch]]]
            [taoensso.timbre :as timbre]
            [clojurewerkz.urly.core :as urly]))

(timbre/set-config! [:appenders :standard-out :async?] true)

(def default-options
  {;; Crawler user agent. Required
   :user-agent nil
   
   ;; Seed URLs for crawl. Required
   :urls nil 

   ;; A channel on which to put crawled pages. Required
   :handler nil

   ;; How many URLs do we allow to queue up before dropping them?
   ;; This you should really configure yourself by experiment / memory
   ;; available.
   :queue-length 10000 

   ;; Default time to wait between page fetches from the same domain
   ;; (in seconds).
   :default-crawl-delay 1

   ;; Robots.txt
   
   ;; A function that takes a user-agent string and a url and returns
   ;; whether or not to crawl it based on the /robots.txt file.
   :crawlable? robots/crawlable?

   ;; A function which takes a user-agent and a url a returns the
   ;; crawl-delay for that domain. 
   :crawl-delay robots/crawl-delay

   ;; A function which takes a user-agent and a response from http-kit
   ;; and returns true iff the headers and META tags allow indexing.
   :meta-index? robots/meta-index?

   ;; A function which takes a user-agent and an http-response and
   ;; returns true iff the headers and META tags allow crawling of
   ;; links on that page.
   :meta-follow? robots/meta-follow?

   ;; Crawl strategy

   ;; apriori filter on urls. If you want to limit your crawl to a
   ;; fixed set of domains, here's where to do it. This fn is called
   ;; when a url is first enqueued.
   :acceptable? (constantly true)

   ;; This function is called with a url just before that url is to be
   ;; scrapped. Return falsy to skip it.
   :crawl? (constantly true)

   ;; A function which takes the body of an HTML page and returns a
   ;; seqable of anchors from the page.
   :get-links (constantly [])

   ;; A channel onto which the http-response map for each page crawled
   ;; will be put!. The consumer of the channel can use it for
   ;; nothing, to make sure the same url is not crawled twice, or
   ;; anything else it dreams up.
   :crawled-ch (chan (async/dropping-buffer 1))})

(def ^:private domain-crawlers (ref {}))

(defn- start-domain-fetcher
  [{:keys [user-agent url-ch handler queue-length
           crawl-delay meta-index? meta-follow?
           crawl? get-links crawled-ch]}]
  (let [domain-ch (chan (async/dropping-buffer queue-length))]
    (go-loop []
      (when-let [next (<! domain-ch)]
        (when (<! (crawl? next))
          (let [page (<! (fetch next))]
            (when (meta-index? agent (:body page))
              (>! handler page)
              (>! crawled-ch page))
            (when (meta-follow? agent (:body page))
              (<! (async/onto-chan url-ch (get-links (:body page)) false)))
            ;; TODO: Account for the time it took to make the call and
            ;; subtract that fromthe wait.
            (<! (async/timeout (crawl-delay user-agent next)))))
        (recur)))
    domain-ch))

(defn- shut-down! []
  (doseq [[k v] @domain-crawlers]
    (async/close! v)))

;; TODO: Can you generalise this to not need cleanup? I.e. ensure that
;; the side-effectful function only ever gets called once?
(defn get-or-set!
  "If a channel exists for the given domain return it, otherwise
  create a new one, store it and return it."
  [opts domain]
  (if-let [val (get @domain-crawlers domain)]
            val
            (let [new-ch (start-domain-fetcher opts domain)
                  {:keys [retrieved? val]}
                  (dosync
                   (if-let [val (get (ensure domain-crawlers) domain)]
                     {:retrieved? true :val val}
                     {:retrieved? false
                      :val (alter domain-crawlers assoc domain new-ch)}))]
              (when retrieved?
                (async/close! new-ch))
              val)))


(defn- smart-q!
  [{:keys [crawlable? acceptable?] :as opts} url]
  (when (and (crawlable? url)
             (acceptable? url))
    (let [domain (urly/host-of url)
          domain-ch (get-or-set! opts domain)]
      (put! domain-ch url)))) 

(defn- crawler* [opts]
  (let [pre-q (chan 100)
        options (assoc opts :url-ch pre-q)]
    (go-loop []
      (if-let [url (<! pre-q)]
        (do
          (smart-q! options url)
          (recur))
        (shut-down!)))
    (async/onto-chan pre-q (:urls options) false)
    ;; Returning the feed ch allows the caller to add more URLs later,
    ;; or shut down the process by closing it.
    pre-q))

(defn crawler
  "Returns a channel which will queue URLs for crawling. This should
  be used for (re)seeding the search."
  [opts]
  (assert (:user-agent opts))
  (assert (:handler opts))
  (assert (and (coll? (:urls opts)) (not-empty (:urls opts))))
  (assert (every? fn? (juxt [:acceptable? :crawl? :get-links :crawled-ch
                             :crawlable? :crawl-delay :meta-index?
                             :meta-follow?] opts)))
  (let [options (merge default-options opts)]
    (crawler* options)))
