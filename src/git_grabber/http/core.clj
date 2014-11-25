(ns git-grabber.http.core
  (:require [environ.core :refer [env]]
            [clj-http.client :as client]
            [clojure.walk :refer [keywordize-keys]]
            [cheshire.core :refer [generate-string decode]]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(declare authorized-request)

;;;; HELPERS

;; #TODO (1*) handle end of data from request
(defn lazy-request-with-pagination [params page request-fn]
  (let [response (request-fn params page)]
    (when (not (empty? response))
      (lazy-cat response (lazy-request-with-pagination params
                                                       (inc page)
                                                       request-fn)))))

;;;; API ADAPTER

;; GIT SEARCH

(def settings {:search-params {:q "language:clojure" :per_page 100}
               :search-url "https://api.github.com/search/repositories"
               :repos-url "https://api.github.com/repos/"
               :users-url "https://api.github.com/users/"})

(defn search-repos
  ([params page]
   (let [request-params (merge (:search-params settings) params {:page page})]
     (-> (authorized-request (:search-url settings)
                             {:query-params request-params
                              :as :json})
         :body
         :items))))

(defn lazy-search-repos
  ([search-params] (lazy-search-repos search-params 1))
  ([search-params page]
   (lazy-request-with-pagination search-params page search-repos)))

;; REPOSITORY

;; #TODO make with delay for separated extractions
(defn get-repository-info-from-github [repository-path]
  (-> (authorized-request (str (:repos-url settings) repository-path)
                          {:as :json})
                :body))

; COMMITS

(defn get-repository-commits [repository-path page]
  (-> (authorized-request (str (:repos-url settings) repository-path "/commits")
                          {:query-params {:page page :per_page 100}
                           :as :json})
      :body))

(defn lazy-get-repository-commits
  ([repo-path] (lazy-get-repository-commits repo-path 1))
  ([repo-path page]
   (lazy-request-with-pagination repo-path page get-repository-commits)))

(defn get-commits-for-date-range [repository-path first-date last-date]
    (:body (authorized-request (str (:repos-url settings) repository-path "/commits")
                               {:query-params {:since first-date :until last-date }
                                :as :json
                                })))

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
