(ns git-grabber.storage.repository
  (:require [korma.core :refer :all]
            [git-grabber.storage.config :refer :all]
            [clojure.set :refer [rename-keys]]
            [git-grabber.storage.owner :refer [get-owner-id-with-name]]))

(defentity repositories)

(def repository-fields [:id :name :description :size :fork
                        :created_at :updated_at :pushed_at])

(defn get-all-repositoies-paths []
  (select repositories (fields :full_name)))

(defn get-repository-id-with-path [path]
  (select repositories (fields :id) (where {:full_name path}) (limit 1)))

(defn prepare-repository-owner [fields repository]
  (if-let [owner-id (get-owner-id-with-name (-> repository :owner :login))]
    (merge fields {:owner_id (-> owner-id first :id)})
    fields))

(defn update-repository [repository]
  (let [prepared-fields (rename-keys (select-keys repository repository-fields)
                                     {:id :github_id, :fork :is_fork})]
    (update repositories
            (set-fields (prepare-repository-owner prepared-fields repository))
            (where (select-keys repository [:full_name])))))
