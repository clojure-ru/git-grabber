(ns git-grabber.storage.owners
  (:require [korma.core :refer :all]
            [git-grabber.storage.config :refer :all])
  (:refer-clojure :exclude [update]))

(defentity owners)

(defn get-owner-id-with-name [owner-name]
  (-> (select owners (fields :id) (where {:name owner-name}) (limit 1))
      first
      :id))
