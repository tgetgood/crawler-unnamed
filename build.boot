;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Dependencies
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def dependencies
  '[[org.clojure/clojure "1.7.0-beta2"]
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

    [http-kit.fake "0.2.1"]
    [org.clojure/test.check "0.7.0"]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Global config
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def project-name "formic")
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

;; Lifted from https://github.com/boot-clj/boot/wiki/Cider-REPL
(deftask cider
  "Add middleware for EMACS Cider integration"
  []
  (require 'boot.repl)
  (swap! boot.repl/*default-dependencies*
         conj '[cider/cider-nrepl "0.8.2"])

  (swap! boot.repl/*default-middleware*
         (fnil into []) '[cider.nrepl/cider-middleware])
  identity)

(deftask clj-refactor
  "Add middleware for clj-refactor.
  N.B.: This depends on the above cider middleware."
  []
  (require 'boot.repl)
  (swap! boot.repl/*default-dependencies*
         conj '[refactor-nrepl "1.0.5"])

  (swap! boot.repl/*default-middleware*
         (fnil into []) '[refactor-nrepl.middleware/wrap-refactor])
  identity)

(deftask emacs
  "Start a repl configured for emacs dev"
  []
  (comp (cider) (clj-refactor) (repl)))

;; TODO: Jar, deploy, test. Production pod without dev deps
