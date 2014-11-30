(ns git-grabber.core
  (:require [git-grabber.evolution.collect :refer [sleep-collect collect]]
            [git-grabber.evolution.updating :refer [update-repositories-info
                                                    update-repositories-counters]]
            [clj-time.core :as t]
            [git-grabber.config :refer :all]
            [taoensso.timbre :as timbre]
            [clojure.tools.cli :refer [parse-opts]]
            [git-grabber.evolution.recover :refer [recover-counters-from-interval]])
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

;; #TODO or deftest???
;; #TODO move to config
(defn recover [from to]
  (let [interval-error #(do (prn %) (prn "Please check interval and try again.") (System/exit 1))
        message #(interval-error
                  (str "Date of " % " interval is broken. Format yyyy-mm-dd:yyyy-mm-dd"))
        ]
    (when-not from
      (interval-error (message  "start")))
    (when-not to
      (interval-error (message  "end")))
    (when (t/after? from to)
      (interval-error (str "Start date after end date.")))
    (when (< (t/in-days (t/interval from to)) 3)
      (interval-error (str "interval should be longer than 2 days.")))
    (prn "all-ok")
  (doall (recover-counters-from-interval from to))))

(defn run-execution-protocol [tasks-keys]
  (cond
   (:recover tasks-keys)  (recover (-> tasks-keys :recover first)
                                   (-> tasks-keys :recover second))
   :else (doall (map #(execute-task tasks-keys %) execution-protocol))))
