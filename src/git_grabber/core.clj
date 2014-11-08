(ns git-grabber.core
  (:require [git-grabber.evolution.harvesting :refer [sleep-harvesting]]))

(defn -main [& args]
  (future (sleep-harvesting 600000 "updated"))
;;   (future  (sleep-harvesting 600000 {}) ;; pagging bug
  (future (sleep-harvesting 1800000 "stars"))
  (future (sleep-harvesting 1800000 "forks")))


