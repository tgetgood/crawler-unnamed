(ns formic.core
  (:import [com.codahale.metrics MetricFilter])
  (:require [clojure.core.async :as async :refer [<! >! chan go go-loop put!]]
            [formic.robots :as robots]
            [formic.utils :refer [fetch]]
            [formic.links :refer [extract-followable-links]]

            [metrics.core :refer [new-registry]]
            [metrics.gauges :refer [gauge-fn]]
            [metrics.counters :refer [counter inc! dec!]]
            [metrics.reporters.console :as console]
            [metrics.meters :refer [defmeter mark!]]

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
   :crawl? (fn [x] (let [ch (chan)] (put! ch true) ch))

   ;; A function which takes the body of an HTML page and returns a
   ;; seqable of anchors from the page.
   :get-links extract-followable-links

   ;; A channel onto which the http-response map for each page crawled
   ;; will be put!. The consumer of the channel can use it for
   ;; nothing, to make sure the same url is not crawled twice, or
   ;; anything else it dreams up.
   :crawled-ch (chan (async/dropping-buffer 1))})

(def ^:private domain-crawlers (ref {}))

(def reg (new-registry))

(defmeter reg smarties)

(def active-domains (gauge-fn reg "Domains being crawled"
                              #(count @domain-crawlers)))

(defonce reporter
  (let [CR (console/reporter reg {:filter MetricFilter/ALL})]
    (console/start CR 20)))

(def test-opts {:user-agent "testy-crawl" :handler (chan (async/dropping-buffer 10)) :urls ["http://allafrica.com/stories/201505220927.html"]})

(defn- start-domain-fetcher
  [{:keys [user-agent url-ch handler queue-length
           crawl-delay meta-index? meta-follow?
           crawl? get-links crawled-ch default-crawl-delay]}
   domain]
  (let [counter (counter reg (str domain " queue size"))
        in-ch (chan)
        domain-ch (chan (async/dropping-buffer queue-length))]
    (go-loop []
      (if-let [v (<! in-ch)]
        (do
          (inc! counter)
          (>! domain-ch v))
        (async/close! domain-ch)))
    (go-loop []
      (when-let [next (<! domain-ch)]
        (dec! counter)
        (when (<! (crawl? next))
          (let [page (<! (fetch next))]
            (when (meta-index? agent (:body page))
              (>! handler page)
              (>! crawled-ch page))
            (when (meta-follow? agent (:body page))
              (<! (async/onto-chan url-ch (get-links (:body page) next) false)))
            ;; TODO: Account for the time it took to make the call and
            ;; subtract that fromthe wait.
            (<! (async/timeout (crawl-delay user-agent next
                                            default-crawl-delay)))))
        (recur)))
    in-ch))

(defn- shut-down! []
  (timbre/log :info "Shutting down domain-crawlers") 
  (doseq [[k v] @domain-crawlers]
    ;;TODO: Do I have to drain these channels to let the crawler shut
    ;;down quickly?
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
                      :val (get (alter domain-crawlers assoc domain new-ch)
                                domain)}))]
              (when retrieved?
                (async/close! new-ch))
              val)))

(defn- smart-q!
  [{:keys [crawlable? acceptable? user-agent] :as opts} url]
  (when (and (crawlable? user-agent url)
             (acceptable? url))
    (mark! smarties)
    (let [domain (urly/host-of url)
          domain-ch (get-or-set! opts domain)]
      (put! domain-ch url)))) 

(defn- crawler* [opts]
  (assert (every? fn? ((juxt :acceptable? :crawl? :get-links 
                             :crawlable? :crawl-delay :meta-index?
                             :meta-follow?) opts)))
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
  (assert (:user-agent opts) "A crawler must define a user-agent")
  (assert (:handler opts)
          "You haven't defined a crawler, any work done will be wasted.")
  (assert (and (coll? (:urls opts)) (not-empty (:urls opts))))
  
  (let [options (merge default-options opts)]
    (crawler* options)))

