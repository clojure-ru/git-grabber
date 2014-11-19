(ns git-grabber.core
  (:require [git-grabber.evolution.collect :refer [sleep-collect]]
            [git-grabber.config :refer :all]))

(def short-sleep-period 1000) ;; 600000) ;; 10 min
(def long-sleep-period 1800000) ;; 30 min

(defn -main [& args]
  (configure)
  (future (sleep-collect short-sleep-period "updated"))
;;   (future  (sleep-collect short-sleep-period {}) ;; #TODO pagging bug
  (future (sleep-collect long-sleep-period "stars"))
  (future (sleep-collect long-sleep-period "forks")))

;; #TODO the Reloaded pattern for start updating and collect with LifeCycle protocol
;; http://martintrojer.github.io/clojure/2013/09/07/retrofitting-the-reloaded-pattern-into-clojure-projects/
(defprotocol LifeCycle
  (start [this])
  (stop [this]))

(defn start-system [system]
  (doseq [s (->> system :order (map system))]
    (start s)))

(defn stop-system [system]
  (doseq [s (->> system :order (map system) reverse)]
    (stop s)))

(defrecord Grabber [state]
  LifeCycle
  (start [_]
         (reset! state (-main)))
  (stop [_]
        (when @state
          (-main)
          (reset! state nil))))

(defn run-Grabber []
  (->Grabber (atom nil)))
