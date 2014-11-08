(ns git-grabber.evolution.updating
  (:require [git-grabber.http.core :refer [get-repository-info-from-github]]
            [git-grabber.storage.repository :refer [get-all-repositoies-paths
                                                    get-repository-id-with-path
                                                    update-repository]]
            [clj-time.core :as t]
            [git-grabber.storage.counters :refer [get-counter-types-ids
                                                  get-counter-repository-for-date]]))


(defn update-repository-info [repository-name]
  (update-repository (get-repository-info-from-github (:full_name repository-name))))

(defn update-repositories-info []
  (pmap #(update-repository-info %)  (get-all-repositoies-paths)))

(defn calc-counter [repo-id counter-id current-val]
  (let [yesterday (t/minus (t/today) (t/days 1))
        old-val (get-counter-repository-for-date repo-id
                                                 counter-id
                                                 yesterday)]
    {:counter_id counter-id
     :increment (if (not-empty old-val)
                  (- current-val (-> old-val first :count))
                  current-val)
     :count current-val}))

(defn update-counters [repository]
  (let [date (t/today)
        repo-id (-> (get-repository-id-with-path (:full_name repository))
                    first
                    :id)
        counter-types-ids (get-counter-types-ids)]
    (prn
     (merge {:date date :repository_id repo-id}
            (calc-counter repo-id
                          (:forks get-counter-types-ids)
                          (:forks repository))))));; (update forks)
;;     (put-unique counters
;;   (update-watchers)
;;   (update-stars)
;;   (update-commits)
