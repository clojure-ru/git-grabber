(ns git-grabber.core
  (:require [git-grabber.evolution.collect :refer [sleep-collect collect]]
            [git-grabber.evolution.updating :refer [update-repositories-info
                                                    update-repositories-counters]]
            [git-grabber.config :refer :all]
            [taoensso.timbre :as timbre]
            [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))

(declare run-execution-protocol)

(defn -main [& args]
  (configure)
  (timbre/info "====== START ======")
  (let [params (:options (parse-opts args cli-options))]
    ;; #TODO make print help
    (if (empty? params)
      (run-execution-protocol {:collect true
                               :information true
                               :counters true})
      (run-execution-protocol params)))
  (timbre/info "====== FINISH ======"))

;; for :collect (collect {}) ;; #TODO collect best repositories from github
(def execution-protocol
  "Ordered algorithm"
  {:collect #(doall (pmap collect ["updated" "stars" "forks"]))
   :information #(doall (update-repositories-info))
   :counters #(doall (update-repositories-counters))})

(defn execute-task [tasks-keys operation]
  (timbre/info (str "------ Execute task: "  (subs (str (key operation)) 1) " ------"))
  (when ((key operation) tasks-keys) ((val operation)))
  (timbre/info (str "------ Task complited: "  (subs (str (key operation)) 1) " ------")))

(defn run-execution-protocol [tasks-keys]
  (doall (map #(execute-task tasks-keys %) execution-protocol)))
