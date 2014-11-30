(ns git-grabber.evolution.recover
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :refer [to-sql-date
                                     to-local-date
                                     from-sql-date
                                     from-string]]
            [clj-time.predicates :refer [same-date?]]
            [korma.core :as kc]
            [git-grabber.storage.counters :refer [get-dates-without-counters
                                                  recover-commit
                                                  recover-commits
                                                  recover-counters-by-abs-counts
                                                  counters-ids
                                                  inc-day
                                                  dec-day
                                                  get-min-date
                                                  date-formater
                                                  number-of-counter-types
                                                  recover-not-changed-counter
                                                  get-repositories-without-counters-for-interval]]
            [git-grabber.storage.repositories :refer [get-all-repo-paths-and-ids]]
            [git-grabber.http.core :refer [authorized-request
                                           get-commits-for-date-range]])
  (:refer-clojure :exclude [update]))


;; RECOVERY | VERSION 1

;; ALG:
;; 1. generate intervals for recover
;; 2. select repositories without counters     2 <-------- with garbage
;; 3. get info from GIT
;; 4. group commits by date and insert
;; 5. get intervals from db                    5 <-------- very bad
;; 6. interpolate values and insert

;; PROBLEMS
;; 1. Includes longer interval
;; 2. Includes cases without limit (top or bottom)

(defn get-date-range-before-today [date]
  (let [today (t/today)]
    (when (t/before? date today)
      (cons date (get-date-range-before-today (t/plus date (t/days 1)))))))

(defn get-prepared-dates []
  (->> (get-min-date)
       to-local-date
       get-date-range-before-today
       (map #(str "(date '" (to-sql-date %) "')"))
       (clojure.string/join ", ")))

(defn yester-or-same-day [before day]
  (or (= (t/plus before (t/days 1)) day)
      (same-date? before day)))

;; #TODO maybe recur?
(defn group-with-dates
  ([access rest-list]
   (when-not (empty? rest-list)
     (group-with-dates [(first rest-list)] (rest rest-list) access)))
  ([acc rest-list access]
   (if-let [next-element (first rest-list)]
     (if (yester-or-same-day (access (last acc)) (access next-element))
       (group-with-dates (conj acc next-element) (rest rest-list) access)
       (cons acc (group-with-dates [next-element] (rest rest-list) access)))
     (list acc))))

(defn get-commit-date-key [commit]
  (subs (-> commit :commit :committer :date) 0 10))

(defn get-commit-date [commit]
  (from-string (-> commit :commit :committer :date)))

(defn commits-group-by-dates [elem-key in-list]
  "elem-key - function: return string key for group elements"
  (reduce #(merge-with concat %1 (hash-map (keyword (elem-key %2)) [%2]))
          {} in-list))

;; #TODO move to counters


(defn dates-for-counter [counter-key dates]
  (map :date (filter #(= (:id %) (counter-key counters-ids)) dates)))

(defn make-intervals-for-recover [from to]
  (->> (get-dates-without-counters from to)
       (group-with-dates identity)
       (map #(list (first %) (last %)))))

(defn recover-commits-for-interval [repo-id-path interval]
  (let [begin (first interval)
        end (or (second interval) (t/plus (first interval) (t/days 1)))]
    (->> (get-commits-for-date-range (:path repo-id-path) begin end)
         (commits-group-by-dates get-commit-date-key) ;; reduce
         (map #(list (name (first %)) (count (second %))))
         (recover-commits (:id repo-id-path) (:commits counters-ids)))))

;; (defn recover-dates-

;; TODO get from db repositories full_name and id
;; (defn recover-all-counters [from to]
;;   (let [dates (make-intervals-for-recover from to)
;;         recover-commits (fn [repo-id-path]
;;                           (map #(recover-commits-for-interval repo-id-path %) dates))]
;;     (pmap recover-commits (get-all-repo-paths-and-ids))
;;     (recover-other-counters dates)
;;     ))


;; RECOVERY | VERSION 2

;; ALG:
;; 1. Get repositories without counters from interval   1 <-------- is slow
;; 2. Make intervals for each repos
;; 3. get commits for intervals and store
;;   3.1. recover increment for repos
;; 4. for other: interpolate values and store

;; PROBLEMS

;; 1. slowly request to db
;; 2. large data
;; 3. interval must be over three days

(defn convert-sql-from-utc [date]
  (let [d (t/to-time-zone (from-sql-date date) (t/default-time-zone))]
    (t/date-time (t/year d) (t/month d) (t/day d))))

;; #TODO problem with time-zone
(defn prepare-jdbc-array-dates [dates]
  (map convert-sql-from-utc (seq (.getArray dates))))

;; #TODO logic error in interval
(defn must-recover-counters? [all-days dates]
  (let [dates# (prepare-jdbc-array-dates dates)]
    (> all-days (count dates#))))


;; #TODO OR NOT in-days with intervals???
(defn missing-dates? [begin end]
  (not (= (t/plus begin (t/days 1)) end)))

;; #TODO make interval as hash
(defn make-interval-for-recover [dates counts]
  (->> dates
       prepare-jdbc-array-dates
       (map #(list %2 %1) (seq (.getArray counts)))
       ((fn [intv] (map #(when (missing-dates? (first %1) (first %2)) [%1 %2])
                        intv (rest intv))))
       (filter identity) first))

;; #TODO GITHUB API bug???
;==========================
(defn test-commit-date [from to commit]
  (let [commit-date (get-commit-date-key commit)]
    (not (or (=  commit-date to)
             (t/before? (f/parse date-formater to)
                        (f/parse date-formater commit-date))
             (=  commit-date from)
             (t/before? (f/parse date-formater from)
                        (f/parse date-formater commit-date))))))

(defn filter-commits [from to commits]
  (filter #(test-commit-date from to %) commits))

;==========================

;; #TODO recover one day
(defn recover-commits-from-github [counter interval]
  (let [from (f/unparse date-formater (inc-day (ffirst interval)))
        to (f/unparse date-formater (dec-day (first (second interval))))
        commits (filter-commits from to (get-commits-for-date-range (:full_name counter) from to))]
    (if-not (empty? commits)
      (->> commits
           (commits-group-by-dates get-commit-date-key)
           (map #(hash-map :counter_id (:counter_id counter)
                           :repository_id (:repository_id counter)
                           :increment (count (val %))
                           :date (-> % key name from-string to-sql-date)
                           ))
           (reduce #(conj %1 (assoc %2 :count (+ (:count (last %1)) (:increment %2))))
                   [{:count (second (first interval))}])
           rest
           (recover-commits (second interval)))
      (recover-counters-by-abs-counts counter interval)))) ;; when GITHUB API error

(defn counter-not-changed? [interval]
  (< (Math/abs (- (second (second interval)) (second (first interval)))) 2))

(defn is-commits? [counter-id]
  (= counter-id (:commits @counters-ids)))

(defn recover-counter [counter]
  (let [interval (make-interval-for-recover (:dates counter) (:counts counter))]
      (when interval
        (if (counter-not-changed? interval)
          (recover-not-changed-counter counter interval)
          (if (is-commits? (:counter_id counter))
            (recover-commits-from-github counter interval)
            (recover-counters-by-abs-counts counter interval)
            )))))

(defn may-recover [counter]
  (let [interval (make-interval-for-recover (:dates counter) (:counts counter))]
    (when (and interval (counter-not-changed? interval))
      counter)))

;; #TODO must-recover not working
;; (defn recover-all-counters-for-repos [repo-counters]
;;   (when (must-recover-counters? number-of-days (:dates (first repo-counters)))
;;     ( repo-counters))

(defn recover-counters-from-interval [from to]
   (->> (get-repositories-without-counters-for-interval from to)
        (pmap #(recover-counter %))))

;; OTHER METHODS

;; (defn recovery-counters []
;;   (let [dates (get-dates-without-counters)
;;         dates-commits (map :date (filter #(= (:id %) (:commits counters-ids)) dates))
;;         grouped-dates (commits-group-by-dates dates-commits #(f/unparse date-formater %))]


  ;; get repositories without commits [date repos]
  ;; get last commits count for date
  ;; group repos and map increments
  ;; put info

;;   )

;; (defn get-dates-for-recovery-counter []
;;   (->> (get-dates-without-counters)
;;        (dates-for-counter :commits)
;;        (group-with-dates identity)))

;; #TODO make select with repositories
;; (defn get-dates-without-counters []
;;   (let [dates (get-prepared-dates)
;;         request (str "(SELECT dates.column1 as date, id FROM (VALUES " dates " ) as dates, counter_types)
;;                      EXCEPT
;;                      (SELECT date, counter_id from counters)" )]
;;    (map (fn [d] (assoc d :date (from-sql-date (:date d))))
;;     (kc/exec-raw request :results))))



;; todo get repositories without counters for rage of dates
;;(defn get-repositories

;; (defn zip [list-1 list-2]
;;   (when (not (or  (empty? list-1)
;;                   (empty? list-2)))
;;     (cons [(first list-1) (first list-2)] (zip (rest list-1) (rest list-2)))))

;; (defn recovery-counters []
;;   (let [dates (get-dates-without-counters)
;;         dates-commits (map :date (filter #(= (:id %) (:commits counters-ids)) dates))
;;         grouped-dates (commits-group-by-dates dates-commits #(f/unparse date-formater %))]


  ;; get repositories without commits [date repos]
  ;; get last commits count for date
  ;; group repos and map increments
  ;; put info

;;   )

;; (defn append-repository-commits [])
