(ns git-grabber.storage.counters
  (:require [korma.core :refer :all]
            [clj-time.core :as t]
            [git-grabber.storage.config :refer :all]))

(defentity counter_type)

(defentity counters)

(defn get-counter-types-ids []
 (-> (map (fn [m] {(keyword (:name m)) (:id m)}) (select counter_type))
     merge
     reduce))


(defn get-counter-repository-for-date [repo-id counter-id date]
  (select counters
          (fields :count)
          (where {:date date
                  :repository_id repo-id
                  :counter_id counter-id})))
