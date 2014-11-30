(ns git-grabber.config
  (:require [taoensso.timbre :as timbre]
            [clj-time.core :as t]
            [clj-time.coerce :refer [from-string]]
            [clojure.java.io :as io]))

(.addShutdownHook (Runtime/getRuntime) (Thread. #(timbre/info "====== KEYBOARD INTERRUPT ======")))

(defn configure []
  (when (not (.isDirectory (io/file "./log")))
    (when (not (.mkdir (io/file "./log")))
      (throw Exception "Can not create directory for log files"))))

;; #TODO use merge-config! for new set of settings
(timbre/set-config! [:appenders :standard-out :enabled?] false)
(timbre/set-config! [:appenders :spit :enabled?] true)
(timbre/set-config! [:shared-appender-config :spit-filename]
                    (str "./log/" (t/today) ".log"))

(timbre/set-config! [:timestamp-pattern] "yyyy-MM-dd HH:mm:ss ZZ")

(def short-sleep-period 600000) ;; 10 min
(def long-sleep-period 1800000) ;; 30 min

(defn parse-recover-interval [time-with-column-sep]
  (let [interval (clojure.string/split time-with-column-sep #":")]
    (map from-string interval)))

(def cli-options
  [["-C" nil "collect information from github search"
    :id :collect
    :defult true]
   ["-I" nil "update repository information"
    :id :information
    :defult true]
   ["-U" nil "update repository counters"
    :id :counters
    :defult true]
   [nil "--recover FROM" "recover repository counters"
    :id :recover
    :parse-fn parse-recover-interval]
   ["-h" "--help"]])
