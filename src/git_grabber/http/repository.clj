(ns git-grabber.http.repository
    (:require [git-grabber.http.core :as core]
              [taoensso.timbre :as timbre]  
              ))

(timbre/refer-timbre)

;;;; HELPERS

;; #TODO (1*) handle end of data from request
(defn lazy-request-with-pagination [params page request-fn]
  (let [response (request-fn params page)]
    (when (not (empty? response))
      (lazy-cat response (lazy-request-with-pagination params
                                                       (inc page)
                                                       request-fn)))))

(defn search-repos
  ([params page]
   (let [request-params (merge (:search-params core/settings) params {:page page})]
     (-> (core/authorized-request (:search-url core/settings)
                             {:query-params request-params
                              :as :json})
         :body
         :items))))

(defn lazy-search-repos
  ([search-params] (lazy-search-repos search-params core/start-page))
  ([search-params page]
   (lazy-request-with-pagination search-params page search-repos)))

;; #TODO make with delay for separated extractions
(defn get-repository-info-from-github [repository-path]
  (-> (core/authorized-request (str (:repos-url core/settings) repository-path)
                          {:as :json})
                :body))

(defn get-repository-commits [repository-path page]
  (-> (core/authorized-request (str (:repos-url core/settings) repository-path "/commits")
                          {:query-params {:page page :per_page core/max-result-for-page}
                           :as :json})
      :body))

(defn lazy-get-repository-commits
  ([repo-path] (lazy-get-repository-commits repo-path 1))
  ([repo-path page]
   (lazy-request-with-pagination repo-path page get-repository-commits)))

(defn log-commits-overload [repo-path first-date last-date page]
  (info (str "//// skip commits over " (* core/max-result-for-page page)
             " for " repo-path " between " first-date " and " last-date)))

;; #TODO up log to upper frame
(defn get-commits-for-date-range
  ([repository-path first-date last-date]
   (get-commits-for-date-range
    repository-path first-date last-date core/start-page))
  ([repository-path first-date last-date page]
    (let [url (str (:repos-url core/settings) repository-path "/commits")
          params {:query-params {:since first-date
                                 :until last-date
                                 :per_page core/max-result-for-page
                                 :page page}
                  :as :json}
          commits (:body (core/authorized-request url params))]
;;       (when (empty? commits) (throw (Exception. (str "url " url " params " params))))
      (if (< (count commits) core/max-result-for-page)
        commits
        (do
          (log-commits-overload repository-path first-date last-date page)
          (lazy-cat commits (get-commits-for-date-range repository-path
                                                        first-date
                                                        last-date
                                                        (inc page))))))))

