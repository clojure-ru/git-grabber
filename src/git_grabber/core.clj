(ns git-grabber.core
  (:require [git-grabber.evolution.collect :refer [sleep-collect]]))

(defn -main [& args]
  (future (sleep-collect 600000 "updated"))
;;   (future  (sleep-collect 600000 {}) ;; pagging bug
  (future (sleep-collect 1800000 "stars"))
  (future (sleep-collect 1800000 "forks")))
