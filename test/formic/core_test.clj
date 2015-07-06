(ns formic.core-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :refer [for-all]]
            [formic.test.gen :refer [url]]
            [formic.test.util :refer [fake-fetch]]

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



