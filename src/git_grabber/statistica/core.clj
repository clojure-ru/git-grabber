(ns git-grabber.statistica.core
  (:require [git-grabber.storage.counters :refer [get-best-repositories]]
            [clj-time.core :as t]
            [plumbing.core :as pc]
            [clojure.pprint :refer [print-table]]
            [cheshire.core :refer [generate-string]]
            [plumbing.graph :as graph]
            [git-grabber.utils.dates :refer [prepare-jdbc-array-dates]]))


(def limit-for-weekly-select 1000)

(defn sum2 [fnn values probabilities]
  (apply + (map #(* (fnn %1) %2) values probabilities)))

(defn sum [fnn values]
  (apply + (map #(fnn %1) values)))

(pc/defnk my-weight [increments n]
  (float (apply + (map #(/ (* %1 n) %2) increments (range n 1 -1))))) 
 
(pc/defnk my-weight2 [increments sum n]
  (apply + (map #(/ (Math/sqrt (/ (* %1 n) sum)) %2) increments (range n 1 -1)))) 

(pc/defnk my-weight3 [increments sum n]
  (float (apply + (map #(/ (* %1 n) sum %2) increments (range n 1 -1))))) 

(def stats-graph {:name   (pc/fnk [full_name]  full_name)
   :incrs  (pc/fnk [increments] increments)
   :n      (pc/fnk [increments] (count increments))
   :sum    (pc/fnk [increments] (apply + increments))
   :freq   (pc/fnk [increments] (frequencies increments))
   :moda   (pc/fnk [freq]       (first (sort-by val > freq)))
;; :fmoda  (pc/fnk [moda]       (sort-by val > (frequencies moda)))
   :w      my-weight
   :w2     my-weight2
   :w3     my-weight3

;;    :min   (pc/fnk [increments]            (reduce min increments))
;;    :max   (pc/fnk [increments]            (reduce max increments))
;; PROBABILITY
   :incs  (pc/fnk [increments]            (map inc increments))
;;   :probs (pc/fnk [incs n]                (map #(/ (val %) n) (frequencies incs)))
;;    :m     (pc/fnk [increments probs]      (sum2 identity increments probs))
;;    :m2    (pc/fnk [increments probs m]    (- (sum2 #(* % %) increments probs) (* m m)))
;;    :m3    (pc/fnk [increments probs m2 m] (+ (* 2 m m m)
;;                                              (- (sum2 #(* % % %) increments probs)
;;                                                 (* 3 m2 m))))
   })

(def stats (graph/eager-compile stats-graph))

(defn prepare-data [repos]
 (let [dates (prepare-jdbc-array-dates (:dates repos))
       counts (seq (.getArray (:increments repos)))]
  (assoc repos :dates dates :increments counts)))

(defn make-statistics []
  (let [from (t/minus (t/today) (t/days 8))
        to (t/today)]
    (map prepare-data (get-best-repositories from to limit-for-weekly-select))))

(defn get-min-freq [x]
  (get (:freq x) (:min x)))

(defn gigants [statics]
  (->>  (group-by :min statics)
        (map #(hash-map (key %) (group-by get-min-freq (val %))))
        (into (sorted-map))))

(defn calculate-globals [statistica]
 (let [init-stat {:max-size-of-incrs -10000}]
  (reduce (fn [res repo-stat]
           (assoc res :max-size-of-incrs (max (count (:incrs repo-stat)) (:max-size-of-incrs res))))
   init-stat statistica)))   

(defn generate-and-sort [sort-key filter-fn]
  (let [statistica (map stats (make-statistics))
        globals (calculate-globals statistica)]
   (sort-by sort-key > (filter #(filter-fn globals %) statistica))))

(defn length-filter [{size :max-size-of-incrs} repo-stat]
  (when (= (count (:incrs repo-stat)) size)
    repo-stat))

;; JSON GENERATE

(def keys-for-json #(select-keys % [:name :incrs]))

(defn generate-json-stat[statistica]
  (spit "stat.json" (generate-string (map keys-for-json statistica))))


;; TEST PRINT

(def keys-for-print #(select-keys % [:sum :incrs :moda :w3 :w2 :w :name]))

(defn print-fmt  [xs]
  (map (pc/fnk [name w sum moda incrs]
         (prn (str name " " sum "  " (seq incrs) " " (seq moda) " " w)))
       xs))

(defn print-tbl [xs]
 (print-table (map keys-for-print xs)))

(defn sort-and-print-frequencies [xs]
  (prn "------------------")
  (prn (str "min-val " (key xs)))
  (doall
   (map #(do
           (prn (str " -------- frequency for min-val " (key %) " ------------ " ))
           (doall (print-fmt (take 10 (sort-by :sum > (val %)))))) (val xs))))

(defn sort-and-print-probs [xs]
  (prn " ------ MAX expected value ------ ")
  (doall (map print-fmt (take 10 (sort-by :m > xs))))
  (prn " ------ MAX dispersion ------ ")
  (doall (map print-fmt  (take 10 (sort-by :m2 < xs))))
  (prn " ------ MAX MOM ------ ")
  (doall (map print-fmt (take 10 (sort-by :m3 > xs)))))

(defn moda-filter [freq-moda xs]
  (filter #(= (-> % :moda second) freq-moda) xs))

(defn sort-by-moda [xs x]
  (prn " -------- MODA ---------- ")
  ;;(print-fmt )
  (print-table (map keys-for-print (take 100 (sort-by #(-> % :sum) > (moda-filter x xs))))))

;; END TEST PRINT

(defn best-increments []
  (map stats (filter #(= (:counter_id %) 4) (make-statistics))))
;;    (doall (map sort-and-print-frequencies (gigants ws)))
;;    (sort-and-print-probs (filter #(> 50 (:sum %)) ws))
    ;;(sort-by-moda ws)

(defn classifaction [sort-key]
  (generate-and-sort sort-key length-filter))
