(ns git-grabber.storage.core
  "Highlevel module for calculate and storage data"
  (:require [git-grabber.storage.counters :refer :all]
            [git-grabber.storage.owner :refer :all]
            [git-grabber.storage.repository :refer :all]
            [git-grabber.storage.config :refer :all]
            [clj-time.core :as t]))


;; when need request to github, extract :increment and :count to top level
(defn update-counters [repository]
  (let [date (t/today)
        repo-id (-> (get-repository-id-with-path (:full_name repository))
                    first
                    :id)
        counter-types-ids (get-counter-types-ids)]
;;     (put-unique counters
    (prn
     (merge {:date date :repository_id repo-id}
            (calc-counter repo-id
                          (:forks get-counter-types-ids)
                          (:forks repository)))
         )) ;; (update forks)
;;   (update-watchers)
;;   (update-stars)
;;   (update-commits)
  )
