(ns git-grabber.storage.config
  (:require [korma.db :refer :all]
            [korma.core :refer :all]
            [environ.core :refer [env]]))

(defdb db (postgres (:connection env)))

;; SQLState: 23505 for duplicates
(defn put [ent data]
  (try
    (insert ent (values data))
    (catch Exception e (prn (.getMessage e)))))

(defn put-unique [ent data]
  (when (= 0 (count (select ent (where data))))
    (prn data)
    (insert ent (values data))))
