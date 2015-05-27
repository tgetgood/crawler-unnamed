(ns formic.links
  (:require [clojurewerkz.urly.core :as urly])
    (:import org.jsoup.Jsoup
             [org.jsoup.nodes Document Element]))

;;;;;
;; The following is adapted from Crawlista.
;; (https://github.com/michaelklishin/crawlista)
;;;;;

(defn strip-fragment
  [^String s]
  (.replaceAll s "\\#.*$" ""))

(defn- ^String href-from
  [^Element a]
  (.attr ^Element a "href"))

(defn- urls-from
  [anchors]
  (map  href-from anchors))

(defn- followable?
  [^Element anchor]
  (let [rel-value (.attr anchor "rel")]
    (or (nil? rel-value)
        (not (= "nofollow"
                (-> rel-value .toLowerCase .trim))))))

(defn- extract-anchor-elements
  "Extracts anchor elements from HTML body"
  [^String body]
  (.getElementsByTag ^Document (Jsoup/parse body) "a"))

(defn- extract-urls-from-anchors
  "Returns the set of unique absolute URLs corresponding to the given
  anchor elements and page URL."
  [anchors uri]
  (let [hrefs (urls-from anchors)]
    (set (map (fn [^String s]
                (strip-fragment (urly/absolutize s uri)))
           hrefs))))

(defn extract-links
  "Returns set of URLs linked from page."
  [body uri]
  (extract-urls-from-anchors (extract-anchor-elements body) uri))

(defn extract-followable-links
  "Returns set of URLs not marked \"nofollow\" linked to from page."
  [body uri]
  (extract-urls-from-anchors
   (filter followable? (extract-anchor-elements body))
   uri))

