(ns git-grabber.core
  (:require [git-grabber.http.core :refer [lazy-search-repos request]]
            [git-grabber.storage :refer [put owners repositories]]))

(declare sleep-harvesting)

(defn -main [& args]
  (future
    (sleep-harvesting 60000 "updated")
    (sleep-harvesting 60000 {})
    (sleep-harvesting 1800000 "stars")
    (sleep-harvesting 1800000 "forks")))

; Specific path /notifications/threads/:id

(defn get-notifications []
  ;;   [all participating since] or {:all :participating :since} ???
  (request "notifications"))

(defn lazy-seq-of-sorted-repos
  "sort - stars, forks or updated, without sort - best matches"

  ([] (lazy-seq-of-sorted-repos {}))
  ([sort-request] (lazy-search-repos {:sort sort-request})))

(defn get-two-houndred-updated-repos []
  (distinct (map #(:name %) (take 200 (lazy-seq-of-sorted-repos)))))

;==== KORMA ====

(defn insert-owner-name [repo]
  (put owners {:name (-> repo :owner :login)}))

(defn insert-repo-path [repo]
  (put repositories (select-keys repo [:full_name])))

(defn harvesting
  ([sort-request]
   (doall (map (fn [r] (insert-owner-name r) (insert-repo-path r))
         (take 1000 (lazy-seq-of-sorted-repos sort-request))))))

(defn cycle-harvesting
  ([] (cycle-harvesting {}))
  ([request] (while true (harvesting request))))

(defn sleep-harvesting [sleep-time sort-request]
  (harvesting sort-request)
  (Thread/sleep sleep-time)
  (recur sleep-time sort-request))
