(ns beagle.corner-case-phrases-test
  (:require [clojure.test :refer [deftest is]]
            [beagle.phrases :as phrases])
  (:import (org.jsoup Jsoup)))

(deftest corner-cases
  (let [annotator (phrases/highlighter [{:text          "N-Able N-Central"
                                       :case-sensitive? false}])
        text (some-> (Jsoup/parse (slurp "test/resources/phrases.html")) (.body) (.text))]
    (is (empty? (annotator text)))))
