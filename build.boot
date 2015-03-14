;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Dependencies
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def dependencies
  '[[org.clojure/clojure "1.7.0-alpha5"]
    [org.clojure/core.async "0.1.346.0-17112a-alpha"]
    
    [org.jsoup/jsoup "1.8.1"]
    [clojurewerkz/urly "1.0.0"]
    [clj-robots "0.6.0"]
    [clj-time "0.9.0"]
    [http-kit "2.1.19"]

    [org.clojure/core.memoize "0.5.6"]
    [org.clojure/core.cache "0.6.4"]
    [metrics-clojure "2.4.0"]
    [com.taoensso/timbre "3.4.0"]])

(def dev-dependencies
  '[[org.clojure/tools.nrepl "0.2.7"]
    [org.clojure/tools.namespace "0.2.10"]

    [org.clojure/test.check "0.7.0"]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Global config
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def project-name "crawler")
(def version "0.1.0")

(set-env! :source-paths #{"src"}
          :dependencies (into [] (concat dependencies dev-dependencies)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Dev functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(require '[clojure.tools.namespace.repl :as repl])

(defn refresh-repl []
  (apply repl/set-refresh-dirs (get-env :directories))
  (repl/refresh))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tasks
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftask cider
  "Add middleware for EMACS Cider integration"
  []
  (require 'boot.repl)
  (swap! boot.repl/*default-dependencies*
         concat ['[cider/cider-nrepl "0.8.2"]])

  (swap! boot.repl/*default-middleware*
         conj 'cider.nrepl/cider-middleware)
  identity)

;; TODO: Jar, deploy, test. Production pod without dev deps
