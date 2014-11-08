(ns git-grabber.http.core
  (:require [environ.core :refer [env]]
            [clj-http.client :as client]
            [clojure.walk :refer [keywordize-keys]]
            [cheshire.core :refer [generate-string decode]]))


(declare request handle-error)

;; GIT AUTH

(def ^:dynamic *token*
  "Auth token for Git API" nil)

;; 1. test -Dtoken for list
;; 2. make lazy cyclic token list

(def sleep-preiod
  "Sleep time when limit of requests has expired"
  12000) ;; 2 minutes

(defn get-token []
  "Get token from enivironment variable."
  (or (first (:token env)) (throw (Exception. "specify -Dtoken="))))

(defmacro with-auth [& body]
  `(binding [*token* ~(get-token)]
     ~@body))

;;;; API ADAPTER

;; GIT SEARCH

(def settings {:search-params {:q "language:clojure" :per_page 100}
               :search-url "https://api.github.com/search/repositories"
               :repos-url "https://api.github.com/repos/"
               :users-url "https://api.github.com/users/"})

(defn search-repos
  ([params]
   (with-auth
    (let [request (merge (:search-params settings) params)]
      (try (-> (client/get (:search-url settings)
                           {:headers {"Authorization" (str "token " *token*)}
                            :query-params request :as :json})
               :body
               :items)
        (catch Exception e (handle-error e) (Thread/sleep sleep-preiod)))))))

(defn lazy-search-repos
  ([params] (lazy-search-repos params 1))
  ([params page]
   (lazy-cat (search-repos (assoc params :page page))
             (lazy-search-repos params (inc page)))))


;;;; VARIANTS
;;; with message
;; - estimated-limit = 100
;; - bad request

;;; not have message
;; - 404 on breking connection

; write formated log for errors: [cur-time][slip-time in msec] message
; throw error when exceeded limit of results

(defn handle-error [error]
    (prn (-> (.getData error)
             :object :body
             decode keywordize-keys
             :message)))


;; REPOSITORY



(defn get-repository-info-from-github [repository-path]
  (with-auth
    (try (-> (client/get (str (:repos-url settings) repository-path)
                         {:headers {"Authorization" (str "token " *token*)}
                          :as :json})
             :body)
     (catch Exception e (handle-error e) (Thread/sleep sleep-preiod)))))

;; REQUEST TO GIT

; Type of path
; /action
; /repos/:owner/:repo/action
; /repos/:owner/:repo/issues/event

(defn request
  "Git API adapter"
  ([path] (request path {}))
  ([path params]
   {:pre [(and (string? path) (map? params))]}
   (with-auth
     (client/get (str "https://api.github.com/" path)
                  {:headers {"Authorization" (str "token " *token*)}
                   :content-type :json
                   :accept :json
;;                    :body (generate-string params)
                   }))))
