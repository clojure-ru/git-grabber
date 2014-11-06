(ns git-grabber.storage
  (:require [korma.db :refer :all]
            [korma.core :refer :all]
            [environ.core :refer [env]]))

(defdb db (postgres (:connection env)))

(defentity owners)

(defentity )

(defn put [ent data]
  (try
    (insert ent (values data))
    (catch Exception e (prn (.getMessage e)))))
