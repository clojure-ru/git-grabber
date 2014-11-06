(ns git-grabber.core
  (:require [git-grabber.http.core :refer [lazy-search-repos request]]))

(defn -main [& args]
  "I'll be OK")

; Specific path /notifications/threads/:id

(defn get-notifications []
  ;;   [all participating since] or {:all :participating :since} ???
    (request "notifications"))

(defn lazy-seq-of-updated-sorted-repos []
  (lazy-search-repos {:sort "updated"}))

(defn get-two-houndred-updated-repos []
  (distinct (map #(:name %) (take 200 (lazy-seq-of-updated-sorted-repos)))))
