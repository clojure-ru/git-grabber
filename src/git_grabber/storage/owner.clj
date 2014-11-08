(ns git-grabber.storage.owner
  (:require [korma.core :refer :all]
            [git-grabber.storage.config :refer :all]))

(defentity owners)

(defn get-owner-id-with-name [owner-name]
  (select owners (fields :id) (where {:name owner-name}) (limit 1)))
