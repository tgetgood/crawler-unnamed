(ns formic.crawl
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


(defn crawler [{:keys [user-agent follow? handler]
                strategy [{:keys [crawl? crawled]}]}]
  (let [waiting (chan (async/dropping-buffer 10000))
        candidates (chan 100)
        ;; The following hold full scraped html (they're big)
        results (chan 100) 
        multi-res (async/mult results)
        indexer-ch (chan 100)
        extractor-ch (chan 100)]

    ;; TODO: To close or not to close?
    (async/tap multi-res handler false)
    (async/tap multi-res indexer-ch)
    (async/tap multi-res extractor-ch)
    (async/reduce (fn [_ x] (crawled x)) nil indexer-ch)
    
    (async/pipeline 4 waiting
                    (-> (map robots/meta-index?)
                        (mapcat extract-followable-links)
                        ;; other filters
                        )
                    extractor-ch)

    (async/pipeline 1 candidates (map crawl?) waiting)
    (async/pipeline-async 16 results fetch candidates)
    
    ))

