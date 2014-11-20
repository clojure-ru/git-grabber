(ns git-grabber.core
  (:require [git-grabber.evolution.collect :refer [sleep-collect collect]]
            [git-grabber.evolution.updating :refer [update-repositories-info
                                                    update-repositories-counters]]
            [git-grabber.config :refer :all]
            [taoensso.timbre :as timbre]
            [clojure.tools.cli :refer [parse-opts]]))

(declare run-execution-protocol)

(defn -main [& args]
  (configure)
  (when (= (count args) 0)
    (prn "Add token and db connection-params")
    (System/exit 1))
  (let [params (:options (parse-opts args cli-options))]
    ;; #TODO make print help
    (if (empty? params)
      (run-execution-protocol {:collect true
                               :information true
                               :counters true})
      (run-execution-protocol params))))

;; for :collect (collect {}) ;; #TODO collect best repositories from github
(def execution-protocol
  "Ordered algorithm"
  {:collect #(doall (pmap collect ["updated" "stars" "forks"]))
   :information #(doall (update-repositories-info))
   :counters #(doall (update-repositories-counters))})

(defn execute-task [tasks-keys operation]
  (when ((key operation) tasks-keys) ((val operation))))

(defn run-execution-protocol [tasks-keys]
  (doall (map #(execute-task tasks-keys %) execution-protocol))) ;; execution bug in lein runtime

;; #TODO the Reloaded pattern for start updating and collect with LifeCycle protocol
;; http://martintrojer.github.io/clojure/2013/09/07/retrofitting-the-reloaded-pattern-into-clojure-projects/
;; (defprotocol LifeCycle
;;   (start [this])
;;   (stop [this]))

;; (defn start-system [system]
;;   (doseq [s (->> system :order (map system))]
;;     (start s)))

;; (defn stop-system [system]
;;   (doseq [s (->> system :order (map system) reverse)]
;;     (stop s)))

;; (defrecord Grabber [state]
;;   LifeCycle
;;   (start [_]
;;          (reset! state (-main)))
;;   (stop [_]
;;         (when @state
;;           (-main)
;;           (reset! state nil))))

;; (defn run-Grabber []
;;   (->Grabber (atom nil)))
