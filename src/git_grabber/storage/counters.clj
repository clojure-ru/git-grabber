(ns git-grabber.storage.counters
  (:require [korma.core :refer :all]
            [clj-time.coerce :refer [to-sql-date
                                     from-sql-date
                                     to-local-date]]
            [clj-time.core :as t]
            [clj-time.periodic :as p]
            [clj-time.format :as f]
            [git-grabber.storage.repositories :refer [repositories
                                                      get-repository-id-by-path]]
            [git-grabber.storage.config :refer :all])
  (:refer-clojure :exclude [update]))

(declare get-counter-types-ids-with-names)

(def date-formater (f/formatter "YYYY-MM-dd"))

(def counters-ids (delay (get-counter-types-ids-with-names)))

(def number-of-counter-types (delay (count @counters-ids)))

;; HELPERS

(defn midnight-date [date]
  (t/date-time (t/year date) (t/month date) (t/day date)))

(defn date-range
  "Return a lazy sequence of DateTime's from start to end, incremented by day."
  [start end]
  (let [inf-range (p/periodic-seq start (t/days 1))
        below-end? #(t/within? (t/interval start end) %)]
    (take-while below-end? inf-range)))

(defn inc-day [date]
  (t/plus date (t/days 1)))

(defn dec-day [date]
  (t/minus date (t/days 1)))

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

(defn count-counters-for-date [date]
  (-> (select counters
              (aggregate (count :*) :*)
              (where {:date (to-sql-date date)}))
      first :*))

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
  (let [from# (to-sql-date from)
        to# (to-sql-date to)]
    (-> (select* counters)
        (fields :repositories.full_name :counter_id :repository_id
                (raw "array_agg(count order by date) as counts")
                (raw "array_agg(date order by date) as dates"))
        (join repositories (= :repositories.id :repository_id))
        (where {:date [between [from# to#]]})
        (group :repositories.full_name :counter_id :repository_id)
        (exec)
    )))

(defn get-dates-without-counters [from to]
  (filter #(= 0 (count-counters-for-date %))
          (date-range (midnight-date from) (midnight-date to))))

;; SETTERS

(defn update-counter [repo-id counter-id counter-value]
  (put counters {:date (to-sql-date (t/today))
                 :repository_id repo-id
                 :counter_id counter-id
                 :count counter-value}))

(defn recover-commit [repo-id counter-id dates-with-commits]
  (when-not (empty? dates-with-commits)
    (let [map-values (fn [[date commit-count]] (hash-map :repository_id repo-id
                                                         :date (to-sql-date (f/parse date-formater date))
                                                         :counter_id counter-id
                                                         :increment commit-count))]
      (insert counters (values (map map-values dates-with-commits))))))

(defn recover-not-changed-counter [counter interval]
  (let [counts (second (first interval))
        dates (date-range (inc-day (ffirst interval))
                          (first (second interval)))]
    (-> (insert* counters)
        (values
         (map #(hash-map :counter_id (:counter_id counter)
                         :repository_id (:repository_id counter)
                         :increment 0
                         :count counts
                         :date (to-sql-date %)
                         ) dates))
         (exec))))

;; #TODO move to recover.clj

;; #TODO optimize! remove date
(defn calculate-increment [interval start-count map-with-counts]
  (rest (reduce (fn [res pair]
            (conj res (assoc pair :increment
              (- (:count pair) (:count (last res))))))
       [{:date (ffirst interval) :count start-count}] map-with-counts)))

(defn make-counter-map [counter begin count-val date]
  {:counter_id (:counter_id counter)
   :repository_id (:repository_id counter)
   :date (to-sql-date date)
   :count (+ (int count-val) begin)
   })

(defn recover-last-increment [value-for-last counter-map]
    (update counters
        (set-fields {:increment (- (second value-for-last)
                                   (:count counter-map))})
        (where {:date (to-sql-date (first value-for-last))
                :counter_id (:counter_id counter-map)
                :repository_id (:repository_id counter-map)})))

(defn recover-counters-by-abs-counts [counter interval]
;; counter {:dates #<Jdbc4Array {2014-11-22,2014-11-23,2014-11-28}>
;;          :counts #<Jdbc4Array {480,480,482}>
;;          :repository_id 44
;;          :counter_id 2,
;;          :full_name "name"}
;; interval [(#<DateTime 2014-11-23T01:00:00.000+07:00> 480)
;;           (#<DateTime 2014-11-28T01:00:00.000+07:00> 482)]
  (let [start-count (second (first interval))
        dates (date-range (inc-day (ffirst interval)) (first (second interval)))
        step (/ (- (second (second interval)) start-count) (count dates))
        counter-map (map #(make-counter-map counter start-count %1 %2)
                                   (iterate #(+ step %) 0) dates)]
    (insert counters
            (values (calculate-increment interval start-count counter-map)))
    (recover-last-increment (second interval) (last counter-map))))

;; #TODO good method.
(defn recover-commits [value-for-last counter-map]
    (insert counters (values counter-map))
    (recover-last-increment value-for-last (last counter-map)))

;; FEATURES

;; недостающие счетчики
;; select repositories.* from repositories left join counters on (id = repository_id) where counter_id is null

