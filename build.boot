;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Dependencies
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def dependencies
  '[[org.clojure/clojure "1.7.0-alpha5"]
    [org.clojure/core.async "0.1.346.0-17112a-alpha"]
    
    [clj-time "0.9.0"]
    [http-kit "2.1.18"]

    [org.clojure/core.memoize "0.5.6"]
    [org.clojure/core.cache "0.6.4"]
    [metrics-clojure "2.4.0"]
    [com.taoensso/timbre "3.4.0"]])

(def dev-dependencies
  '[[weasel "0.6.0-SNAPSHOT"]
    [com.cemerick/piggieback "0.1.5"]

    [org.clojure/tools.nrepl "0.2.7"]

    [com.cemerick/double-check "0.6.1"]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Global config
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def project-name "crawler")
(def version "0.1.0")

(set-env! :source-paths #{"src"}
          :dependencies dependencies)

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

(deftask dev
  "Runs a repl primed with dev dependencies."
  [c cider-nrepl bool "Start repl with cider-nrepl middleware"]
  (let [{:keys [cider-nrepl]} *opts*]
    (set-env! :dependencies #(into [] (concat % dev-dependencies)))
    (comp
     (if cider-nrepl (cider) identity)
     (repl))))
