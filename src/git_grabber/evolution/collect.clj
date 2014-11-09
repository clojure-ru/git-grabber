(ns git-grabber.evolution.collect
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

(defn insert-owner-name [repository-map]
  (:id (put-unique owners {:name (-> repository-map :owner :login)})))

(defn insert-repository-path [repository-map]
  (put-unique repositories (select-keys repository-map [:full_name])))

(defn insert-data [repository-map]
  (let [owner-id (insert-owner-name repository-map)]
    (insert-repository-path repository-map owner-id)))

(defn collect
  ([sort-request]
   (prn (str "collect: " sort-request)) ;; bug: ""collect: starscollect: updated""
   (doall (map (fn [r] (insert-owner-name r) (insert-repository-path r))
         (take 1000 (lazy-seq-of-sorted-repos sort-request))))))

(defn cycle-collect
  ([] (cycle-collect {}))
  ([request] (while true (collect request))))

(defn sleep-collect [sleep-time sort-request]
  (while true
    (do
     (collect sort-request)
     (Thread/sleep sleep-time))))
