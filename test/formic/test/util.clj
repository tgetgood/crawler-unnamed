(ns formic.test.util
  (:require [org.httpkit.client :as http]
            [org.httpkit.fake :refer [with-fake-http]]
            [clojure.core.async :as async :refer [put! chan]]))

(defn fake-fetch [url]
  (let [res (chan)]
    (with-fake-http [(constantly true) "sold!"]
      (http/get url (fn [x] (put! res x) (async/close! res))))
    res))
