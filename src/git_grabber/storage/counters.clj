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


(defn calc-counter [repo-id counter-id current-val]
  (let [old-val (select counters
                        (fields :count)
                        (where {:date (t/minus (t/today) (t/days 1))
                                :repository_id repo-id
                                :counter_id counter-id}))]
    {:counter_id counter-id
     :increment (if (not-empty old-val)
                  (- current-val (-> old-val first :count))
                  current-val)
     :count current-val}))
