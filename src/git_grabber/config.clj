(ns git-grabber.config
  (:require [clojure.java.io :as io])
  (:import [java.io File]))

(declare get-local-settings)

(def ^:dynamic *settings*
  "Map settings for grabber system
  -file contains same settings, but just local
  -token from git for t-auth"
  {:file "settings",
   :token ""
   })

(defmacro with-local-settings [& body]
  `(binding [*settings* (merge *settings* (get-local-settings))]
    ~@body))

(defn get-local-settings
  "overload settings from file"
  []
  (let [settings-file (io/file (:file *settings*))]
    (if (.exists settings-file)
      (read-string (slurp settings-file))
      (do
        (prn "Settings file not found.
             Set file path in git-grabber.config/*settings*")
        {}))))
