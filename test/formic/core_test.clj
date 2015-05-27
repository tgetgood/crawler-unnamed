(ns formic.core-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :refer [for-all]]
            [formic.gens :refer [url]]

            [org.httpkit.client :as http]
            [org.httpkit.fake :refer [with-fake-http]]
            [clojure.core.async :as async :refer [put! chan]]

            [clojure.string :as string] 

            [formic.core :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;; Network generation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn make-graph [urls]
  (let [connections (mapv
                     (fn [x]
                       (mapv (fn [y] (rand-int (count urls)))
                             (range (rand-int (count urls)))))
                     urls)
        bodies []]))

(def graph
  (gen/fmap make-graph
            (gen/vector url)))

(defn fake-fetch [url]
  (let [res (chan)]
    (with-fake-http [(constantly true) "sold!"]
      (http/get url (fn [x] (put! res x) (async/close! res))))
    res))
