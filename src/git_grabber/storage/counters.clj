(ns git-grabber.storage.counters
  (:require [korma.core :refer :all]
            [clj-time.coerce :refer [to-sql-date from-sql-date]]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [git-grabber.utils.dates :refer
             [inc-day dec-day date-range date-formater]]
            [git-grabber.storage.repositories :refer [repositories]]
            [git-grabber.storage.config :refer :all])
  (:refer-clojure :exclude [update]))

(declare get-counter-types-ids-with-names)

(def counters-ids (delay (get-counter-types-ids-with-names)))

(def number-of-counter-types (delay (count @counters-ids)))

;; COUNTER-TYPES

(defentity counter_types)

(defn get-counter-type-id [name]
  (:id (select counter_types (where {:name name}))))

(defn get-counter-type-ids []
  (map :id (select counter_types)))

(defn get-counter-types-ids-with-names []
  (let [rekey (fn [m] {(keyword (:name m)) (:id m)})]
    (apply merge (map rekey (select counter_types)))))

;; COUNTERS

;; #TODO apply to counters (has-one counter_types).
;; May this joined counter_types with selects automaticaly
(defentity counters)

;; GETTERS

(defn get-repositories-names-without-counters [rdate]
  (let [repo-ids-for-date (subselect counters
                                     (fields :repository_id)
                                     (where {:date (to-sql-date rdate)}))]
    (map :full_name
         (select repositories
                 (fields :full_name)
                 (where {:id [not-in repo-ids-for-date]})))))

;; select repository_id, counter_id, date from counters
;; group by  repository_id, counter_id, date having (count(*)/4 < 8)

;; select * from counters
;; where date between '2014-11-21' and '2014-11-28'
;; group by  repository_id, counter_id, date
;; having (count(*)/4 between 0 and 8)
;; order by repository_id, counter_id, date

;; select  full_name, counter_id, array_agg(count order by date), array_agg(date)
;; from counters left join repositories on (repository_id = id)
;; where date between '2014-11-21' and '2014-11-28'
;; group by counter_id, full_name


(defn get-repositories-without-counters-for-interval [from to]
  (let [from# (to-sql-date from) to# (to-sql-date to)]
    (-> (select* counters)
        (fields :repositories.full_name :counter_id :repository_id
                (raw "array_agg(count order by date) as counts")
                (raw "array_agg(date order by date) as dates"))
        (join repositories (= :repositories.id :repository_id))
        (where {:date [between [from# to#]]})
        (group :repositories.full_name :counter_id :repository_id)
        (exec)
    )))

(defn get-counter-for-date [repo-id counter-id date]
  (-> (select* counters)
      (fields :count)
      (where {:date (to-sql-date date) :repository_id repo-id :counter_id counter-id})
      (limit 1) (exec) first :count))

(defn get-counters-with-null-increments []
  (select counters
      (fields :repository_id :counter_id
              (raw "array_agg(count order by date) as counts")
              (raw "array_agg(date order by date) as dates"))
      (where {:increment nil})
      (group :repository_id :counter_id)))

;; select full_name, counter_id, sum(increment) as incr
;; from counters join repositories on (repository_id = id)
;;  where date between '2014-11-21' and '2014-11-28'
;; group by full_name, counter_id order by incr desc

(defn get-best-repositories [from to selection-limit]
  (select counters
      (fields :repositories.full_name :counter_id (raw "sum(increment) AS incr")
              (raw "array_agg(increment order by date) as increments")
              (raw "array_agg(date order by date) as dates"))
      (join repositories (= :repositories.id :repository_id))
      (where {:date [between [(to-sql-date from) (to-sql-date to)]]})
      (group :repositories.full_name :counter_id)
      (order (raw "incr") :DESC)
      (limit selection-limit)))

;; SETTERS

(defn update-counter [repo-id counter-id counter-value]
  (let [today (t/today)
        old-count (get-counter-for-date repo-id
                                        counter-id
                                        (dec-day today))]
    (put counters {:date (to-sql-date today) :repository_id repo-id
                   :counter_id counter-id :count counter-value
                   :increment (if old-count (- counter-value old-count) 0)
                   })))

(defn recover-commit [repo-id counter-id dates-with-commits]
  (when-not (empty? dates-with-commits)
    (let [map-values #(fn [[date commit-count]]
                        (hash-map :repository_id repo-id
                                  :date (to-sql-date (f/parse date-formater date))
                                  :counter_id counter-id :increment commit-count))]
      (insert counters (values (map map-values dates-with-commits))))))

(defn recover-last-increment
  [date value-for-last {current-count :count repo-id :repository_id cnt-id :counter_id}]
  (update counters
        (set-fields {:increment (- value-for-last (or current-count value-for-last))})
        (where {:date (to-sql-date date)
                :counter_id cnt-id :repository_id repo-id})))

(defn recover-counter-values [counter-map]
  (insert counters (values counter-map)))

(defn recover-increment [counter-map]
  (update counters (set-fields {:increment (:increment counter-map)})
          (where (select-keys counter-map [:repository_id :counter_id :date]))))

;; FEATURES

;; недостающие счетчики
;; select repositories.* from repositories left join counters on (id = repository_id) where counter_id is null

