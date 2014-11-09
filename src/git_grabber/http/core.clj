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

;;;; LOW LEVEL

;; GIT AUTH

(def ^:dynamic *token*
  "Auth token for Git API" nil)

;; 1. test -Dtoken for list
;; 2. make lazy cyclic token list

(def sleep-preiod
  "Sleep time when limit of requests has expired"
  10000) ;; 10 secs

(defn get-token []
  "Get token from enivironment variable."
  (or (first (:token env)) (throw (Exception. "specify -Dtoken="))))

(defmacro with-auth [& body]
  `(binding [*token* ~(get-token)]
     ~@body))

;;; EXCEPTION VARIANTS

;;; with message
;; - estimated-limit = 100
;; - bad request

;;; not have message
;; - 404 when connection is broken

; write formated log for errors: [cur-time][slip-time in msec] message
; throw error when exceeded limit of results

(defn handle-error [error]
  (prn (-> (.getData error)
           :object :body
           decode keywordize-keys
           :message)))

(defn authorized-request
  "Git API adapter"
  ([path] (authorized-request path {}))
  ([path params]
   (with-auth
     (let [auth-params {"Authorization" (str "token " *token*)}]
       (try (client/get path (merge params {:headers auth-params}))
         (catch Exception e (handle-error e) (Thread/sleep sleep-preiod)))))))
