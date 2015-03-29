(ns git-grabber.evolution.recover
  (:require [clj-time.core :as t]
            [clj-time.coerce :refer [to-sql-date from-string]]
            [git-grabber.utils.dates :refer
             [to-format-string from-format-string  yester-or-same-day
              inc-day dec-day date-range diff-in-days prepare-jdbc-array-dates]]
            [git-grabber.storage.counters :refer
             [get-repositories-without-counters-for-interval
              get-counters-with-null-increments recover-increment
              recover-last-increment recover-counter-values
              counters-ids get-counter-for-date]]
            [git-grabber.http.repository :refer [get-commits-for-date-range]]))

;; HELPERS

;; #TODO convert to date ???
(defn get-commit-date-key [commit]
  (subs (-> commit :commit :committer :date) 0 10))

(defn commits-group-by-dates [elem-key in-list]
  "elem-key - function: return string key for group elements"
  (reduce #(merge-with concat %1 (hash-map (keyword (elem-key %2)) [%2]))
          {} in-list))

(defn missing-dates? [begin end]
  (not (= (inc-day begin) end)))

(defn make-interval-for-recover [dates counts]
  (letfn [(map-interval [[from to]]
                        {:from-date (first from)    :to-date (first to)
                         :start-count (second from) :end-count (second to)})]
  (->> dates prepare-jdbc-array-dates
       (map #(list %2 %1) (seq (.getArray counts)))
       ((fn [intv] (map #(when (missing-dates? (first %1) (first %2)) [%1 %2])
                        intv (rest intv))))
       (filter identity) first
       (#(when % (map-interval %))))))

(defn calculate-increment [start-count map-with-counts]
  (rest (reduce #(conj %1 (assoc %2 :increment (- (:count %2) (:count (last %1)))))
       [{:count start-count}] map-with-counts)))

;; RECOVER COUNTERS FROM DB

(defn make-counter-map
  [{repo-id :repository_id cnt-id :counter_id} from to counts]
  (let [dates (date-range (inc-day from) to)]
    (map #(hash-map :counter_id cnt-id :repository_id repo-id :increment 0
                    :date (to-sql-date %1) :count %2)  dates counts)))

(defn recover-counters-by-abs-counts
  [counter {from :from-date to :to-date start :start-count end :end-count}]
  (let [days (diff-in-days from to) step (/ (- end start) days)]
    (->> (map #(+ (int %) start) (iterate #(+ step %) 0))
         (make-counter-map counter from to )
         (calculate-increment start) (recover-counter-values)
         (#(recover-last-increment to end (last %))))))

(defn recover-not-changed-counter
  [counter {from :from-date to :to-date start :start-count}]
  (->> start repeat (make-counter-map counter from to) (recover-counter-values)))

;; RECOVER FROM GITHUB

;; #TODO GITHUB API bug???
;==========================
(defn test-commit-date [from to commit]
  (let [commit-date (get-commit-date-key commit)]
    (not (or (=  commit-date to)
             (t/before? (from-format-string to)
                        (from-format-string commit-date))
             (=  commit-date from)
             (t/after? (from-format-string from)
                       (from-format-string commit-date))))))

(defn filter-commits [from to commits]
  (filter #(test-commit-date from to %) commits))

;==========================

(defn reduce-map [map-key start-value reduce-fn maps]
  (rest (reduce #(conj %1 (assoc %2 map-key (reduce-fn %2 (last %1))))
                   [{map-key start-value}] maps)))

;; #TODO recover one day
(defn recover-commits-from-github
  [{repo-id :repository_id cnt-id :counter_id repo-name :full_name :as counter}
   {from :from-date to :to-date start :start-count end :end-count :as interval}]
  (let [from# (to-format-string (inc-day from))
        to# (to-format-string (dec-day to))
        commits (filter-commits from# to#
                                (get-commits-for-date-range repo-name from# to#))]
    (if-not (empty? commits)
      (->> (commits-group-by-dates get-commit-date-key commits)
           (map #(hash-map :counter_id cnt-id :repository_id repo-id
                           :increment (count (val %))
                           :date (-> % key name from-string to-sql-date)))
           (reduce-map :count start #(+ (:increment %1) (:count %2)))
           (recover-counter-values)
           (#(recover-last-increment to end (last %))))
      (recover-counters-by-abs-counts counter interval)))) ;; when GITHUB API error

(defn counter-not-changed? [{start :start-count end :end-count}]
  (< (Math/abs (- end start)) 2))

(defn is-commits? [counter-id]
  (= counter-id (:commits @counters-ids)))

(defn recover-counter [counter]
  (let [interval (make-interval-for-recover (:dates counter) (:counts counter))]
      (when interval
        (if (counter-not-changed? interval)
          (recover-not-changed-counter counter interval)
          (if (is-commits? (:counter_id counter))
            (recover-commits-from-github counter interval)
            (recover-counters-by-abs-counts counter interval))))))

(defn recover-counters-from-interval [from to]
   (pmap recover-counter (get-repositories-without-counters-for-interval from to)))

;; #TODO set multiple values
(defn recover-nullable-increment [{repo-id :repository_id cnt-id :counter_id
                                   jdbc-dates :dates jdbc-counts :counts}]
  (let [dates (prepare-jdbc-array-dates jdbc-dates)
        first-count (get-counter-for-date repo-id cnt-id
                                          (dec-day (first dates)))
        hs-cnt #(hash-map :counter_id cnt-id :repository_id repo-id
                          :date (to-sql-date %1) :count %2)
        cnts (seq (.getArray jdbc-counts))]
    (->> (map hs-cnt dates cnts)
         (calculate-increment (or first-count (first cnts)))
         (#(doall (map recover-increment %))))))

(defn recover-nullable-increments []
  (doall (pmap recover-nullable-increment (get-counters-with-null-increments))))
