(ns git-grabber.evolution.recover
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :refer [to-sql-date from-sql-date]]
            [korma.core :as kc]
            [git-grabber.storage.counters :refer [get-min-local-date]]
            [git-grabber.http.core :refer [authorized-request]])
  (:refer-clojure :exclude [update]))


(defn get-date-range-before-today [date]
  (let [today (t/today)]
    (when (t/before? date today)
      (cons date (get-date-range-before-today (t/plus date (t/days 1)))))))

(def date-formater (f/formatter "YYYY-MM-dd"))

(defn get-prepared-dates []
  (->> (get-min-local-date)
      get-date-range-before-today
      (map #(str "(date '" (to-sql-date %) "')"))
      (clojure.string/join ", ")))

(defn get-dates-without-counters []
  (let [dates (get-prepared-dates)
        request (str "(SELECT dates.column1 as date, id FROM (VALUES " dates " ) as dates, counter_types)
                     EXCEPT
                     (SELECT date, counter_id from counters)" )]
   (map (fn [d] (assoc d :date (from-sql-date (:date d))))
    (kc/exec-raw request :results))))

;; todo get repositories without counters for rage of dates
;;(defn get-repositories
