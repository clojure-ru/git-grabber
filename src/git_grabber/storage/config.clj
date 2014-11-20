(ns git-grabber.storage.config
  (:require [korma.db :refer :all]
            [korma.core :refer :all]
            [environ.core :refer [env]])
  (:refer-clojure :exclude [update]))

(defdb db (postgres (:connection env)))

;; SQLState: 23505 for duplicates
(defn put [ent data]
  (try
    (insert ent (values data))
    (catch Exception e (prn (.getMessage e)))))

(defn put-unique [ent data]
  (let [old-data (select ent (where data))]
    (if (= 0 (count old-data))
      (insert ent (values data))
      (first old-data))))
