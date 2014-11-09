(defproject git-grabber "0.1.0-SNAPSHOT"
  :description "Calculate statistics for git repos on clojure"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-time "0.8.0"]
                 [clj-http "1.0.1"]
                 [environ "1.0.0"]
                 [cheshire "5.3.1"]
                 [postgresql "9.3-1102.jdbc41"]
                 [korma "0.4.0"]]
  :main ^:skip-aot git-grabber.core
  :target-path "target/%s"
  :injections [(require '[git-grabber.core :refer :all]
                        '[git-grabber.http.core :refer :all]
                        '[git-grabber.storage.config :refer :all]
                        '[git-grabber.evolution.updating :refer :all])
               (defn repass [] (use :reload 'git-grabber.core))]
  :profiles {:uberjar {:aot :all}})
