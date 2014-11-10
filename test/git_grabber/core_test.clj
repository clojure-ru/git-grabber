(ns git-grabber.core-test
  (:require [clojure.test :refer :all]
            [git-grabber.evolution.collect :refer :all]))

(defn disinct-one-thousand-updated-repos []
  (distinct (map #(:name %) (take 1000 (lazy-seq-of-sorted-repos)))))

(deftest a-test
  (testing "Lazy request not for updated"
    (is (> (disinct-one-thousand-updated-repos) 100))))
