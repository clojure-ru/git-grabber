(ns git-grabber.evolution.recover
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :refer [to-sql-date
                                     from-sql-date
                                     from-string]]
            [clj-time.predicates :refer [same-date?]]
            [korma.core :as kc]
            [git-grabber.storage.counters :refer [get-repositories-without-counters-for-interval
                                                  recover-commits
                                                  recover-counters-by-abs-counts
                                                  recover-not-changed-counter
                                                  counters-ids
                                                  inc-day
                                                  dec-day
                                                  date-formater]]
            [git-grabber.storage.repositories :refer [get-all-repo-paths-and-ids]]
            [git-grabber.http.core :refer [authorized-request
                                           get-commits-for-date-range]])
  (:refer-clojure :exclude [update]))


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

(defn commits-group-by-dates [elem-key in-list]
  "elem-key - function: return string key for group elements"
  (reduce #(merge-with concat %1 (hash-map (keyword (elem-key %2)) [%2]))
          {} in-list))

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

(defn recover-counters-from-interval [from to]
   (->> (get-repositories-without-counters-for-interval from to)
        (pmap #(recover-counter %))))
