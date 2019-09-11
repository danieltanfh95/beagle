(ns beagle.phrases
  (:require [clojure.string :as s]
            [beagle.validator :as validator]
            [beagle.annotation-merger :as merger]
            [beagle.dictionary-optimizer :as optimizer]
            [beagle.text-analysis :as text-analysis]
            [beagle.monitor :as monitor])
  (:import (java.util UUID)
           (org.apache.lucene.document Document FieldType Field)
           (org.apache.lucene.index IndexOptions)
           (org.apache.lucene.monitor Monitor MonitorQuery HighlightsMatch HighlightsMatch$Hit)
           (org.apache.lucene.search PhraseQuery)))

(defn match->annotation [text monitor type-name ^HighlightsMatch match]
  (mapcat
    (fn [[_ hits]]
      (let [meta (.getMetadata (.getQuery monitor (.getQueryId match)))]
        (map (fn [hit]
               (let [start-offset (.-startOffset ^HighlightsMatch$Hit hit)
                     end-offset (.-endOffset ^HighlightsMatch$Hit hit)]
                 {:text          (subs text start-offset end-offset)
                  :type          (or (get meta "_type") type-name)
                  :dict-entry-id (.getQueryId match)
                  :meta          (into {} meta)
                  :begin-offset  start-offset
                  :end-offset    end-offset})) hits)))
    (.getHits match)))

(def ^FieldType field-type
  (doto (FieldType.)
    (.setTokenized true)
    (.setIndexOptions IndexOptions/DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
    (.setStoreTermVectors true)
    (.setStoreTermVectorOffsets true)))

(defn annotate-text [^String text ^Monitor monitor field-names ^String type-name]
  (let [doc (Document.)]
    (doseq [field-name field-names]
      (.add doc (Field. ^String field-name text field-type)))
    (mapcat #(match->annotation text monitor type-name %)
            (.getMatches (.match monitor doc (HighlightsMatch/MATCHER))))))

(defn prepare-synonyms [query-id {:keys [synonyms] :as dict-entry}]
  (map (fn [synonym]
         (-> dict-entry
             (assoc :text synonym)
             (dissoc :synonyms)
             (assoc :id (str (UUID/randomUUID)))
             (update-in [:meta] assoc :synonym? "true" :query-id query-id)))
       synonyms))

(defn phrase->strings [dict-entry default-analysis-conf]
  (let [analyzer (text-analysis/get-string-analyzer dict-entry default-analysis-conf)]
    (into-array String (text-analysis/text->token-strings (:text dict-entry) analyzer))))

(defn dict-entry->monitor-query [{:keys [id text meta type slop] :as dict-entry} default-analysis-conf idx]
  (let [query-id (or id (str idx))
        metadata (reduce-kv (fn [m k v] (assoc m (name k) v)) {} (if type (assoc meta :_type type) meta))]
    (MonitorQuery. query-id
                   (if slop
                     (PhraseQuery. slop
                                   (text-analysis/get-field-name dict-entry default-analysis-conf)
                                   (phrase->strings dict-entry default-analysis-conf))
                     (PhraseQuery. (text-analysis/get-field-name dict-entry default-analysis-conf)
                                   (phrase->strings dict-entry default-analysis-conf)))
                   text
                   metadata)))

(defn dictionary->monitor-queries [dict-entries default-analysis-conf]
  (flatten
    (map (fn [{id :id :as dict-entry} idx]
           (let [query-id (or id (str idx))]
             (cons
               (dict-entry->monitor-query dict-entry default-analysis-conf idx)
               (map #(dict-entry->monitor-query % default-analysis-conf nil)
                    (prepare-synonyms query-id dict-entry)))))
         dict-entries (range))))

(defn synonym-annotation? [annotation]
  (= "true" (get-in annotation [:meta "synonym?"])))

(defn meta-type? [annotation]
  (string? (get-in annotation [:meta "_type"])))

(defn post-process [annotation]
  (cond-> annotation
          (synonym-annotation? annotation) (assoc :dict-entry-id (get-in annotation [:meta "query-id"]))
          (meta-type? annotation) (update-in [:meta] dissoc "_type")))

(defn annotator
  "Creates an annotator function with for a given dictionary.
  Params:
  - dictionary: a list of dictionary entries as described in `beagle.schema`
  Options:
  - type-name: a string, defaults to \"PHRASE\"
  - validate-dictionary?: if set to true then validates the dictionary, default false
  - optimize-dictionary?: if set to true then optimizes dictionary before creating the monitor, default false
  - tokenizer: a keyword one of #{:standard :whitespace}, default :standard"
  [dictionary & {:keys [type-name validate-dictionary? optimize-dictionary? tokenizer]}]
  (when validate-dictionary? (validator/validate-dictionary dictionary))
  (let [dictionary (if optimize-dictionary? (optimizer/optimize dictionary) dictionary)
        default-type-name (if (s/blank? type-name) "PHRASE" type-name)
        {:keys [monitor field-names]} (monitor/setup dictionary {:tokenizer tokenizer} dictionary->monitor-queries)]
    (fn [text & {:keys [merge-annotations?]}]
      (if (s/blank? text)
        []
        (let [annotations (map post-process (annotate-text text monitor field-names default-type-name))]
          (if merge-annotations?
            (merger/merge-same-type-annotations annotations)
            annotations))))))
