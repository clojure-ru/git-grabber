(ns git-grabber.evolution.harvesting
(:require [git-grabber.http.core :refer [lazy-search-repos]]
          [git-grabber.storage.repository :refer [repositories]]
          [git-grabber.storage.config :refer [put-unique]]
          [git-grabber.storage.owner :refer [owners]]))

(defn lazy-seq-of-sorted-repos
  "sort - stars, forks or updated, without sort - best matches"

  ([] (lazy-seq-of-sorted-repos {}))
  ([sort-request] (lazy-search-repos {:sort sort-request})))

(defn get-two-houndred-updated-repos []
  (distinct (map #(:name %) (take 200 (lazy-seq-of-sorted-repos)))))

(defn insert-owner-name [repo]
  (put-unique owners {:name (-> repo :owner :login)}))

(defn insert-repository-path [repo]
  (put-unique repositories (select-keys repo [:full_name])))

(defn harvesting
  ([sort-request]
   (prn (str "Harvesting: " sort-request)) ;; bug: ""Harvesting: starsHarvesting: updated""
   (doall (map (fn [r] (insert-owner-name r) (insert-repository-path r))
         (take 1000 (lazy-seq-of-sorted-repos sort-request))))))

(defn cycle-harvesting
  ([] (cycle-harvesting {}))
  ([request] (while true (harvesting request))))

(defn sleep-harvesting [sleep-time sort-request]
  (while true
    (do
     (harvesting sort-request)
     (Thread/sleep sleep-time))))
