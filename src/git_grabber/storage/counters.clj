(ns git-grabber.storage.counters
  (:require [korma.core :refer :all]
            [clj-time.coerce :refer [to-sql-date
                                     from-sql-date
                                     to-local-date]]
            [clj-time.core :as t]

            [git-grabber.storage.repositories :refer [repositories]]
            [git-grabber.storage.config :refer :all])
  (:refer-clojure :exclude [update]))

(defentity counter_types)

;; #TODO apply to counters (has-one counter_types).
;; May this joined counter_types with selects automaticaly
(defentity counters)

(defn get-counter-type-id [name]
  (:id (select counter_types (where {:name name}))))

(defn get-counter-type-ids []
  (map :id (select counter_types)))

(defn get-counter-types-ids-with-names []
  (let [rekey (fn [m] {(keyword (:name m)) (:id m)})]
    (apply merge (map rekey (select counter_types)))))


(defn update-counter [repo-id counter-id counter-value]
  (put counters {:date (to-sql-date (t/today))
                 :repository_id repo-id
                 :counter_id counter-id
                 :count counter-value}))

(defn get-repositories-names-without-counters [rdate]
  (let [repo-ids-for-date (subselect counters
                                     (fields :repository_id)
                                     (where {:date (to-sql-date rdate)}))]
    (map :full_name
         (select repositories
                 (fields :full_name)
                 (where {:id [not-in repo-ids-for-date]})))))

(defn get-min-local-date []
  (-> (select counters (order :date :ASC) (limit 1))
      first :date from-sql-date to-local-date))
