(ns beagle.dictionary-optimizer
  (:require [clojure.set :as set]
            [clojure.string :as str]))

(defn merge-synonyms [group-of-entries]
  (reduce (fn [synonyms-set {synonyms :synonyms}]
            (into synonyms-set synonyms))
          #{} group-of-entries))

(defn merge-meta [group-of-entries]
  (reduce (fn [acc {meta :meta}] (merge acc meta)) {} group-of-entries))

(defn merge-entries [entries]
  (let [{:keys [text case-sensitive? ascii-fold? id]} (first entries)
        synonyms (remove #(= text %) (merge-synonyms entries))
        meta (merge-meta entries)]
    (cond-> {:text text}
            (not-empty synonyms) (assoc :synonyms synonyms)
            (not-empty meta) (assoc :meta meta)
            id (assoc :id id)
            (not (nil? case-sensitive?)) (assoc :case-sensitive? case-sensitive?)
            (not (nil? ascii-fold?)) (assoc :ascii-fold? ascii-fold?))))

(defn mergeable-meta? [{meta-a :meta} {meta-b :meta}]
  (every? #(= (get meta-a %) (get meta-b %)) (set/intersection (set (keys meta-a)) (set (keys meta-b)))))

(defn aggregate-entries-by-meta [entries]
  (loop [entry-a (first entries)
         [entry-b & remaining] (rest entries)
         acc []
         exceptions []]
    (if entry-b
      (if (mergeable-meta? entry-a entry-b)
        (recur (merge-entries [entry-a entry-b]) remaining acc exceptions)
        (recur entry-a remaining acc (conj exceptions entry-b)))
      (if (seq exceptions)
        (recur (first exceptions) (rest exceptions) (conj acc entry-a) [])
        (conj acc entry-a)))))

(defn group-dictionary-entries [dictionary]
  (group-by (fn [entry] [(:text entry) (:case-sensitive? entry) (:ascii-fold? entry)]) dictionary))

(defn optimize [dictionary]
  (mapcat (fn [[_ grouped-entries]] (aggregate-entries-by-meta grouped-entries))
          (group-dictionary-entries dictionary)))

(defn optimization-suggestion [entries]
  {:suggestion       (-> (format "Dictionary items '%s' have identical `[text case-sensitivity ascii-folding] features."
                                 (reduce #(conj %1 (or (:id %2) (:text %2))) [] entries))
                         (str/replace #"\"" ""))
   :dictionary-items entries})

(defn dry-run [dictionary]
  (reduce (fn [acc [_ grouped-entries]]
            (if (< 1 (count grouped-entries))
              (conj acc (optimization-suggestion grouped-entries))
              acc))
       [] (group-dictionary-entries dictionary)))
