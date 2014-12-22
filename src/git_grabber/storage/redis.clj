(ns git-grabber.storage.redis
  (:refer-clojure :exclude [get set])
  (:require [taoensso.carmine :as car :refer (wcar)]
            [environ.core :refer [env]]))

(def server-conn {:pool {} :spec {:host "127.0.0.1" 
                                  :port (let [p (:redis-port env)]
                                          (if (string? p)
                                            (Integer/parseInt p)
                                            p))}})

(defmacro cache [& body] `(car/wcar server-conn ~@body))

(defn get [key] (cache (car/get key)))

(defn set [key val] (cache (car/set key val)) val)
