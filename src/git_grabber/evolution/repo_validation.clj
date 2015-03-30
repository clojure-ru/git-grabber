(ns git-grabber.evolution.repo-validation
    (:require [git-grabber.storage.repositories :as storage]
              [git-grabber.http.repository :refer [is-leiningen-project?]]))

(defn test-repository-for-clojure [repository-path]
  (storage/set-repository-clojure-flag 
    repository-path
    (is-leiningen-project? repository-path)))

(defn test-all-repositories-for-clojure [] 
  (doall (pmap test-repository-for-clojure (storage/get-all-repositories-paths))))

