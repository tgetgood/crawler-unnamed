(ns formic.core
  (:import [com.codahale.metrics MetricFilter])
  (:require [clojure.core.async :as async :refer [<! >! chan go go-loop put!]]
            [formic.robots :as robots]
            [formic.utils :refer [fetch]]
            [formic.links :refer [extract-followable-links]]

            [metrics.core :refer [new-registry]]
            [metrics.gauges :refer [gauge-fn]]
            [metrics.reporters.console :as console]
            [metrics.meters :refer [defmeter mark!]]

            [taoensso.timbre :as timbre]
            [clojurewerkz.urly.core :as urly]))

(timbre/set-config! [:appenders :standard-out :async?] true)

(def default-options
  {;; Crawler user agent. Required
   :user-agent nil
   
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


(defmeter incoming-url)

(def active-domains (gauge-fn "Domains being crawled"
                              #(count @domain-crawlers)))

(defonce reporter
  (let [CR (console/reporter {:filter MetricFilter/ALL})]
    (console/start CR 20)))

(defn domain-filter [& domains]
  (let [dset (into #{} (map urly/host-of domains))]
    (fn [url]
      (contains? dset (urly/host-of url)))))

(def test-opts {:user-agent "testy-crawl"
                :handler (chan (async/dropping-buffer 10))
                :acceptable? (domain-filter "http://allafrica.com")})

(def counts (atom {}))

(def queue-sizes
  (gauge-fn "Domain Queue Sizes" (fn [] @counts)))

(defn- start-domain-fetcher
  [{:keys [user-agent url-ch handler queue-length
           crawl-delay meta-index? meta-follow?
           crawl? get-links crawled-ch default-crawl-delay]}
   domain]
  (let [in-ch (chan)
        domain-ch (chan (async/dropping-buffer queue-length))]
    (go-loop []
      (if-let [v (<! in-ch)]
        (do
          (swap! counts update domain (fnil inc 0))
          (>! domain-ch v)
          (recur))
        (async/close! domain-ch)))
    (go-loop []
      (when-let [next (<! domain-ch)]
        (swap! counts update domain dec)
        (let [next-crawl (chan)]
          (go
            ;; Time the next crawl delay independently of how it takes
            ;; to decide whether or not to crawl this one.
            (<! (async/timeout (crawl-delay user-agent next
                                              default-crawl-delay)))
              (>! next-crawl true))
          (when (<! (crawl? next))
            (let [page (<! (fetch next))]
              (when (meta-index? agent (:body page))
                (>! handler page)
                (>! crawled-ch page))
              (when (meta-follow? agent (:body page))
                (<! (async/onto-chan url-ch (get-links (:body page) next) false)))
              (<! next-crawl))))
        (recur)))
    in-ch))

(defn- shut-down! []
  (timbre/log :info "Shutting down domain-crawlers") 
  (doseq [[k v] @domain-crawlers]
    (async/close! v)
    (go (while (<! v)))))

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
    (mark! incoming-url)
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
    pre-q))

(defn crawler
  "Returns a channel which will queue URLs for crawling. This should
  be used for (re)seeding the search."
  [opts]
  (assert (:user-agent opts) "A crawler must define a user-agent")
  (assert (:handler opts)
          "You haven't defined a handler, any work done will be wasted.")
  
  (let [options (merge default-options opts)]
    (crawler* options)))

