(ns git-grabber.evolution.updating
  (:require [git-grabber.http.core :refer [get-repository-info-from-github
                                           lazy-get-repository-commits]]

            [git-grabber.storage.repositories :refer [repositories
                                                      get-all-repositories-paths
                                                      get-repository-id-by-path
                                                      update-repository-info]]
            [git-grabber.storage.config :refer [put-unique]]
            [git-grabber.storage.counters :refer [counters counter_types
                                                  update-counter
                                                  get-counter-types-ids-with-names
                                                  get-repositories-names-without-counters]]
            [git-grabber.storage.redis :as r]
            [clj-time.core :as t]))


;; #TODO update-owner-info

(defn update-repositories-info []
  "Update general repository information"
  (pmap #(update-repository-info (get-repository-info-from-github %))
        (get-all-repositories-paths)))

(defn count-commits-on-github [repository-path]
  (count (lazy-get-repository-commits repository-path)))

;;;; FOR ALL COUNTERS
(defn update-repository-counters [repository-map]
  (let [repo-id (-> repository-map :full_name get-repository-id-by-path)
        counter-types-ids (get-counter-types-ids-with-names)]
    (when repo-id
     (update-counter repo-id
                     (:forks counter-types-ids)
                     (:forks repository-map))

     (update-counter repo-id
                     (:watchers counter-types-ids)
                     (:watchers repository-map))

     (update-counter repo-id
                     (:stars counter-types-ids)
                     (:stargazers_count repository-map))

     (update-counter repo-id
                     (:commits counter-types-ids)
                     (count-commits-on-github (:full_name repository-map)))
     nil)))

(defn update-repositories-counters []
  (pmap #(update-repository-counters (get-repository-info-from-github %))
        (get-repositories-names-without-counters (t/today))))
