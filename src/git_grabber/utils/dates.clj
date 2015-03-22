(ns git-grabber.utils.dates
  (:require [clj-time.core :as t]
            [clj-time.coerce :as c] 
            [clj-time.format :as f]
            [clj-time.periodic :as p]
            [clj-time.predicates :refer [same-date?]]))

(def minute 60000)

(def date-formater (f/formatter "YYYY-MM-dd"))

(defn from-sql-to-utc [date]
  (c/from-long (- (.getTime date) (* (.getTimezoneOffset date) minute))))

(defn prepare-jdbc-array-dates [dates]
  (map from-sql-to-utc (seq (.getArray dates))))

(defn to-format-string [date]
  (f/unparse date-formater date))

(defn from-format-string [date]
    (f/parse date-formater date))

(defn yester-or-same-day [before day]
  (or (= (t/plus before (t/days 1)) day)
      (same-date? before day)))

(defn inc-day [date]
  (t/plus date (t/days 1)))

(defn dec-day [date]
  (t/minus date (t/days 1)))

(defn diff-in-days [from to]
  (t/in-days (t/interval from to)))

(defn date-range
  "Return a lazy sequence of DateTime's from start to end, incremented by day."
  [start end]
  (let [inf-range (p/periodic-seq start (t/days 1))
        below-end? #(t/within? (t/interval start end) %)]
    (take-while below-end? inf-range)))
