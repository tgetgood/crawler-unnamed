(ns formic.robots-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :refer [for-all]]

            [clojure.string :as string]

            [formic.robots :refer :all]))


