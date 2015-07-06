(ns formic.test.gen
  (:require [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :refer [for-all]]

            [clojure.string :as string]))

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


