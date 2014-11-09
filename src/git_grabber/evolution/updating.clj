(ns git-grabber.evolution.updating
  (:require [git-grabber.http.core :refer [get-repository-info-from-github
                                           lazy-get-repository-commits]]

            [git-grabber.storage.repository :refer [repositories
                                                    get-all-repositoies-paths
                                                    get-repository-id-with-path
                                                    update-repository]]
            [git-grabber.storage.config :refer [put-unique]]
            [git-grabber.storage.counters :refer [counters
                                                  update-counter
                                                  get-counter-types-ids]]
            [clj-time.core :as t]))

(use 'debugger.core)

(defn update-repository-info [repository-name]
  (break
   (update-repository (get-repository-info-from-github repository-name))))

(defn update-repositories-info []
  (pmap #(update-repository-info %) (get-all-repositoies-paths)))



(defn count-commits-on-github [repository-path]
  (count (lazy-get-repository-commits repository-path)))

;; репы, у которых нет каунтеров за сегодня" и опрашивай только их
;; (defn get-repositories-without-counters []
;;   (select repositories (fields :full_name) (join )))

;; (defn update-repositories-counters [])


;; apply destructive pattern for counter-ids
(defn update-counters [repository-map]
  (let [repo-id (-> repository-map :full_name get-repository-id-with-path)
        counter-types-ids (get-counter-types-ids)]
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
                    (count-commits-on-github (:full_name repository-map)))))

