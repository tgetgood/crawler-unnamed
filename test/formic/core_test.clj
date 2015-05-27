(ns formic.core-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :refer [for-all]]

            [org.httpkit.fake :refer [with-fake-http]]
            [clojure.core.async :as async :refer [put! chan]]

            [clojure.string :as string] 

            [formic.core :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;; URL generation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def prot
  (gen/elements ["http" "https"]))

(def non-empty
  (gen/such-that not-empty gen/string-alphanumeric))

(def domain
  (gen/fmap (fn [parts] (string/join "." parts))
            (gen/such-that #(> (count %) 1) non-empty)))

(def path
  (gen/fmap (fn [parts] (str "/" (string/join "/" parts)))
            (gen/vector
             non-empty)))

(def url
  (gen/fmap (fn [[prot dom path]]
              (str prot "://" dom path))
            (gen/tuple prot domain path)))


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
