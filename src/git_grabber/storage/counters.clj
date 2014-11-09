(ns git-grabber.storage.counters
  (:require [korma.core :refer :all]
            [clj-time.coerce :refer [to-sql-date]]
            [clj-time.core :as t]
            [git-grabber.storage.config :refer :all]))

(defentity counter_types)

(defentity counters)

(defn get-counter-types-ids []
  (let [rekey (fn [m] {(keyword (:name m)) (:id m)})]
    (apply merge (map rekey (select counter_types)))))

(defn get-counter-for-date [repo-id counter-id date]
  (select counters
          (fields :count)
          (where {:date (to-sql-date date)
                  :repository_id repo-id
                  :counter_id counter-id})))

(defn calc-increment [repo-id counter-id current-val]
  (let [yesterday (t/minus (t/today) (t/days 1))
        old-val (first (get-counter-for-date repo-id counter-id yesterday))]
    (- current-val (or (:count old-val) 0))))

(defn update-counter [repo-id counter-id counter-value]
  (let [date (t/today)
        increment (calc-increment repo-id counter-id counter-value)]
    (put-unique counters {:date date
                          :repository_id repo-id
                          :counter_id counter-id
                          :incerement increment
                          :count counter-value})))
