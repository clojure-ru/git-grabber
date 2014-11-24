(ns git-grabber.storage.config
  (:require [korma.db :refer :all]
            [korma.core :refer :all]
            [taoensso.timbre :as timbre]
            [environ.core :refer [env]])
  (:refer-clojure :exclude [update]))

(defdb db (postgres {:db "grabber"
                     :user (or (:db-user env) "")
                     :password (or (:db-pass env) "")
                     :debug? false}))

;; SQLState: 23505 for duplicates
(defn put [ent data]
  (try
    (insert ent (values data))
    (catch Exception e (timbre/error (str "| INSERT ERROR | "  (.getMessage e))))))

(defn put-unique [ent data]
    (try
      (insert ent (values data))
      (catch Exception e (timbre/error (str "| duplicate INSERT | {" ent "} <= " data)))))
