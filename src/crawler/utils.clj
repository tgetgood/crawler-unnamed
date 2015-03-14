(ns crawler.utils
  (require [org.httpkit.client :as http]
           [clojure.core.async :as async :refer [>! <! put! go chan go-loop]]))

;;;;;
;; core.async utility functions
;;;;;

(defn tap-it [mult & {:keys [size] :or {:size 1}}]
  (let [ch (chan size)]
    (async/tap mult ch)
    ch))

(defn as-ch [f & {:keys [size] :or {:size 1}}]
  (let [input-ch (chan size)]
    (go-loop []
      (when-let [v (<! input-ch)]
        (f v)
        (recur)))
    input-ch))

(defn flatten-channel
  "Returns a channel containing what would be equivalent to (apply
  concat in-ch) were in-ch a (finite) seq."
  ;; TODO: Is there really no built in for this?
  ;; TODO: Shouldn't I be using a transducer?
  [in-ch]
  (let [out-ch (chan)]
    (go-loop []
      (when-let [v (<! in-ch)]
        (if (coll? v)
          (async/onto-chan out-ch v)
          (>! out-ch v))
        (recur)))
    out-ch))

;;;;;
;; Web helpers
;;;;;

(defn fetch
  "Asynchronously fetches URL and places response map on res-ch"
  [url res-ch]
  (http/get url (fn [x] (put! res-ch x) (async/close! res-ch))))
