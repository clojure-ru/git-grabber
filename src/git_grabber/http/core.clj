(ns git-grabber.http.core
  (:require [environ.core :refer [env]]
            [clj-http.client :as client]
            [clojure.walk :refer [keywordize-keys]]
            [cheshire.core :refer [generate-string decode]]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(declare authorized-request)

;;;; HELPERS

(defn lazy-request-with-pagination [params page request-fn]
  (let [response (request-fn params page)]
    (if (and response (empty? response))
      nil
      (lazy-cat response (lazy-request-with-pagination params
                                                       (inc page)
                                                       request-fn)))))

;;;; API ADAPTER

;; GIT SEARCH

(def settings {:search-params {:q "language:clojure" :per_page 100}
               :search-url "https://api.github.com/search/repositories"
               :repos-url "https://api.github.com/repos/"
               :users-url "https://api.github.com/users/"})

;; #TODO test page limit
;; for example (when: (<= page 10))
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

(defn get-repository-info-from-github [repository-path]
  (let [rep (-> (authorized-request (str (:repos-url settings) repository-path)
                                {:as :json})
            :body)]
    ;; TODO log this
    ;;     (when (not rep)
    ;;       (prn repository-path))
    rep))

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

;;;; LOW LEVEL

;; GIT AUTH

(def ^:dynamic *token*
  "Auth token for Git API" nil)

(def sleep-period
  "Sleep time when limit of requests has expired"
  10000) ;; 10 secs

(defn get-token []
  "Get token from enivironment variable."
  (or (:token env) (throw (Exception. "specify -Dtoken="))))

(defmacro with-auth [& body]
  `(binding [*token* ~(get-token)]
     ~@body))

;; #TODO error log
; write formated log for errors: [cur-time][slip-time in msec] message

(defn handle-error [err path]
  (error (str "{SystemError} (" path ") " (.getMessage err))))

(defn make-request-params [params]
  (merge params {:headers {:Authorization (str "token " *token*)}
                 :throw-exceptions false}))

(defn handle-status-error [message]
  (error message)
  (Thread/sleep sleep-period))

(defn handle-status-bad-response [message]
  (info message)
  (Thread/sleep sleep-period))

(defn handle-status [response path]
  (letfn [(eval-and-sleep [x] (Thread/sleep sleep-period) nil)]
    (case (:status response)
      200 response
      404 (handle-status-error (str "(" path ") "
                                    (get (-> response :body decode) "message")))
      (handle-status-bad-response (str "[" (:status response) "] "
                                       "(" path ") "
                                       (get (-> response :body decode) "message"))))))

(defn authorized-request
  "Git API adapter"
  ([path] (authorized-request path {}))
  ([path params]
   (with-auth
     (try
       (handle-status (client/get path (make-request-params params)) path)
     (catch Exception e (handle-error e path))))))
