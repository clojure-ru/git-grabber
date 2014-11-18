(ns git-grabber.http.core
  (:require [environ.core :refer [env]]
            [clj-http.client :as client]
            [clojure.walk :refer [keywordize-keys]]
            [cheshire.core :refer [generate-string decode]]))

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
    (when (not rep)
      (prn repository-path))
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


;; #TODO
;; 1. test -Dtoken for list
;; 2. make lazy cyclic token list

(def sleep-period
  "Sleep time when limit of requests has expired"
  10000) ;; 10 secs

(defn get-token []
  "Get token from enivironment variable."
  (or (:token env) (throw (Exception. "specify -Dtoken="))))

(defmacro with-auth [& body]
  `(binding [*token* ~(get-token)]
     ~@body))

;;; EXCEPTION VARIANTS

;;; with message
;; - estimated-limit = 100
;; - bad request

;;; not have message
;; - 404 when connection is broken

;; #TODO error log
; write formated log for errors: [cur-time][slip-time in msec] message
; throw error when exceeded limit of results

(defn handle-error [error path]
  (prn (.getMessage error)))

(defn make-request-params [params]
  (merge params {:headers {:Authorization (str "token " *token*)}
                 :throw-exceptions false}))

;; #TODO terminate???
(defn handle-status [response]
  (if (= (:status response) 200)
    response
    (Thread/sleep sleep-period)))

(defn authorized-request
  "Git API adapter"
  ([path] (authorized-request path {}))
  ([path params]
   (with-auth
     (try
       (handle-status (client/get path (make-request-params params)))
     (catch Exception e (handle-error e path))))))
