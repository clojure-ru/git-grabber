(ns git-grabber.http.core
  (:require [environ.core :refer [env]]
            [clj-http.client :as client]
            [clojure.walk :refer [keywordize-keys]]
            [cheshire.core :refer [generate-string decode]]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(declare authorized-request)

;;;; API ADAPTER

;; GIT SEARCH

(def max-result-for-page 100)
(def start-page 1)

(def settings {:search-params {:q "language:clojure" :per_page max-result-for-page}
               :search-url "https://api.github.com/search/repositories"
               :repos-url "https://api.github.com/repos/"
               :users-url "https://api.github.com/users/"
               })

;;;; LOW LEVEL

;; GIT AUTH

(def ^:dynamic *token*
  "Auth token for Git API" nil)

(def sleep-period
  "Sleep time when limit of requests has expired"
  600000) ;; 10 minutes

(defn get-token []
  "Get token from enivironment variable."
  (or (:token env) (throw (Exception. "specify -Dtoken="))))

(defmacro with-auth [& body]
  `(binding [*token* (get-token)]
     ~@body))

;; #TODO error log
; write formated log for errors: [cur-time][slip-time in msec] message

(defn handle-error [err path]
  (error (str "{SystemError} (" path ") " (.getMessage err))))

(defn make-request-params [params]
  (merge params {:headers {:Authorization (str "token " *token*)}
                 :throw-exceptions false}))

(defn log-error-response [response requests-path]
  (error (str "(" requests-path ") "
              (get (-> response :body decode) "message"))))

(defn log-bad-response [response requests-path]
  (info (str "[" (:status response) "] "
             "(" requests-path ") "
             (get (-> response :body decode) "message"))))

(defmulti handle-status
  "Realization of GitHub API response-protocol"
  (fn [{:keys [status]} _ _]
    status))

(defmethod handle-status 200 [response _ _]
  response)

(defmethod handle-status 403 [response path params]
  "API rate limit exceeded for %user%."
  (log-bad-response response path)
  (Thread/sleep sleep-period)
  (authorized-request path params))

(defmethod handle-status 404 [response path _]
  (log-error-response response path)
  nil)

(defmethod handle-status 422 [response path _]
  "Only the first 1000 search results are available"
  (log-error-response response path)
  nil)

(defmethod handle-status :default [response path _]
  (log-bad-response response path)
  nil)

(defn authorized-request
  "Git API adapter"
  ([path] (authorized-request path {}))
  ([path params]
   (with-auth
     (try
       (handle-status (client/get path (make-request-params params)) path params)
       (catch Exception e (handle-error e path)))
   )))

(defn strong-request [path params]
  (let [response (client/get path params)] 
         (case (:status response)
           404 false
           200 (:body response)
           (do 
             (Thread/sleep sleep-period)
             (strong-request path params)))))

(defn github-file-exists? [repository-path file-path]
  (let [path (str (:repos-url settings) 
                  repository-path 
                  "/contents/" file-path)]
    (with-auth
     (try
       (strong-request path (make-request-params {:as :json}))
       (catch Exception e (handle-error e path))))))
