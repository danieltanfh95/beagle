<a href="http://www.tokenmill.lt">
      <img src=".github/tokenmill-logo.svg" width="125" height="125" align="right" />
</a>

# beagle

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![pipeline status](https://gitlab.com/tokenmill/oss/beagle/badges/master/pipeline.svg)](https://gitlab.com/tokenmill/oss/beagle/badges/master)
[![Clojars Project](https://img.shields.io/clojars/v/lt.tokenmill/beagle.svg)](https://clojars.org/lt.tokenmill/beagle)
[![cljdoc badge](https://cljdoc.org/badge/lt.tokenmill/beagle)](https://cljdoc.org/d/lt.tokenmill/beagle/CURRENT)

The detector of interesting things in the text. The intended use is in the stream search applications. Let us say you need to monitor a stream of text documents: web crawl results, chat messages, corporate documents for mentions of various keywords. *Beagle* will help you to quickly set up such system and start monitoring your documents.

Implementation is based on [Lucene monitor](https://github.com/apache/lucene-solr/tree/master/lucene/monitor) library which is based on [Luwak](https://github.com/flaxsearch/luwak).

## Components

- Phrase annotator
- Dictionary file readers (csv, json, edn)
- Dictionary validator
- Dictionary optimizer
- Annotation merger

## Phrase annotator usage

```clojure
(require '[beagle.phrases :as phrases])

(let [dictionary [{:text "to be annotated" :id "1"}]
      annotator (phrases/annotator dictionary :type-name "LABEL")]
  (annotator "before annotated to be annotated after annotated"))
=> ({:text "to be annotated", :type "LABEL", :dict-entry-id "1", :meta {}, :begin-offset 17, :end-offset 32})

(let [dictionary [{:text "TO BE ANNOTATED" :id "1" :case-sensitive? false}]
      annotator (phrases/annotator dictionary :type-name "LABEL")]
  (annotator "before annotated to be annotated after annotated"))
=> ({:text "to be annotated", :type "LABEL", :dict-entry-id "1", :meta {}, :begin-offset 17, :end-offset 32})

(let [dictionary [{:text "TÖ BE ÄNNÖTÄTED" :id "1" :case-sensitive? false :ascii-fold? true}]
      annotator (phrases/annotator dictionary :type-name "LABEL")]
  (annotator "before annotated to be annotated after annotated"))
=> ({:text "to be annotated", :type "LABEL", :dict-entry-id "1", :meta {}, :begin-offset 17, :end-offset 32})
;; Stemming support for multiple languages
(let [dictionary [{:text "Kaunas" :id "1" :stem? true :stemmer :lithuanian}]
        annotator-fn (phrases/annotator dictionary)]
  (annotator-fn "Kauno miestas"))
=> ({:text "Kauno", :type "PHRASE", :dict-entry-id "1", :meta {}, :begin-offset 0, :end-offset 5})
;; Phrases also support slop (i.e. terms edit distance)
(let [txt "before start and end after"
        dictionary [{:text "start end" :id "1" :slop 1}]
        annotator-fn (phrases/annotator dictionary)]
  (annotator-fn txt))
=> ({:text "start and end", :type "PHRASE", :dict-entry-id "1", :meta {}, :begin-offset 7, :end-offset 20})
```

## Java interface

Example:
```java
DictionaryEntry dictionaryEntry = new DictionaryEntry("test phrase");
dictionaryEntry.setSlop(1);
HashMap<String, Object> annotatorOptions = new HashMap<>();
annotatorOptions.put("type-name", "LABEL");
annotatorOptions.put("validate-dictionary?", true);
Annotator annotator = new Annotator(Arrays.asList(dictionaryEntry), annotatorOptions);
HashMap<String, Object> annotationOptions = new HashMap<>();
annotationOptions.put("merge-annotations?", true);
Collection<Annotation> annotations = annotator.annotate("This is my test phrase", annotationOptions);
annotations.forEach(s -> System.out.println("Annotated: \'" + s.text() + "\' at offset: " + s.beginOffset() + ":" + s.endOffset()));
// => Annotated: 'test phrase' at offset: 11:22
```

All the options that are present in the Clojure interface are also available for use in Java. The translation is that both
annotator and annotation options map should have converted Clojure keywords converted to strings, e.g.
```
:case-sensitive? => "case-sensitive?"
```  

### Project Setup with Maven

Add Clojars repository to your `pom.xml`:
```xml
<repositories>
    <repository>
        <id>clojars.org</id>
        <url>https://repo.clojars.org</url>
    </repository>
</repositories>
```

and then the dependency on `beagle`:
```xml
<dependency>
    <groupId>lt.tokenmill</groupId>
    <artifactId>beagle</artifactId>
    <version>0.1.6</version>
</dependency>
```

## Performance

The performance was measured on a modest desktop PC with Ubuntu 19.04, 8-core Ryzen 1700.
 
The test setup was for news articles and dictionary made up of names of city names in USA.

### Single-threaded

Average time spent per document ranged from 1.58 ms for dictionary of 5k phrases to 4.58 ms per document for 80k phrases.

![alt text](charts/st-avg-per-doc.png)

Throughput of docs analyzed ranged from 626 docs/sec for dictionary of 5k phrases to 210 docs/sec for 80k phrases.

![alt text](charts/st-throughput-per-sec.png)

Max time spent per document has couple of spikes when processing a document takes ~1000ms. These spikes should 
have been caused either by GC pauses, or JVM deoptimizations. Aside from those spikes, max time ranges grows slowly
from 15 ms to 72 ms. 

Min time spent per document is fairly stable for any dictionary size and is about 0.45 ms. Most likely these are the
cases when presearcher haven't found any candidate queries to run against the doc. 

![alt text](charts/st-min-max-per-doc.png)

### Multi-threaded

Using `core.async` pipeline time spent per single doc ranged from 3.38 ms for dictionary of 5k phrases to 15.34 ms per document for 80k phrases.

![alt text](charts/mt-avg-per-doc.png)

Total time spent to process all 10k docs ranged from 2412 ms for dictionary of 5k phrases to 12595 ms per document for 80k phrases.

![alt text](charts/mt-total.png)

Throughput of docs analyzed ranged from 4143 docs/sec for dictionary of 5k phrases to 793 docs/sec for 80k phrases.

![alt text](charts/mt-throughput-per-sec.png)

Max time spent per document has risen fairy steady from 24.15 ms for dictionary of 10k phrases to 113.45 ms per document for 60k phrases.

Min time spent per document varied from 0.6 ms for dictionary of 10k phrases to 1.1 ms per document for 55k phrases.

![alt text](charts/mt-min-max-per-doc.png)

Code for benchmarking and more benchmarks can be found [here](https://github.com/tokenmill/beagle-performance-benchmarks).

## Dictionary readers

Three file formats are supported: csv, edn, json.

### CSV dictionary format

Separator: ","
Escape: "\""

The first line *MUST* be a header.

Supported header keys: `["text" "type" "id" "synonyms" "case-sensitive?" ":ascii-fold?" "meta"]`

Order is not important.

Under `synonyms`, there should be a list of string separated by ";"
Under `meta`, there should be a list of strings separated by ";". Even number of strings is expected. In case of odd number, last one is ignored.

## Validator

Accepts any number of dictionaries to validate as long as they are provided in pairs as '"/path/to/dictionary/file" "file-type"'

### Supported file types

- csv
- json
- edn

### Output

- If any dictionary is invalid exception will be thrown with exit status 1

### Usage

#### Clojure

To use validator directly execute command: `clj -m beagle.validator "/path/to/dictionary/file" "file-type" "/path/to/dictionary/file2" "file-type" & ...`

##### Example:

```
clj -m beagle.validator "your-dict.csv" "csv" "your-other-dict.json" "json"
```

#### Docker

Example in Gitlab CI:

```
validate-dictionaries:
  stage: dictionary-validation
  when: always
  image: registry.gitlab.com/tokenmill/clj-luwak/dictionary-validator:2
  script:
    - >
      dictionary-validator
      /path/to/dict.csv csv
      /path/to/dict.json json
      /path/to/dict.edn edn
```

## Dictionary optimizer

Supported optimizations:
- Remove duplicate dictionary entries
- Merge synonyms
- Synonyms and text equality check

There are cases when dictionary entries can't be merged:
- Differences in text analysis

Examples:
```clojure
(require '[beagle.dictionary-optimizer :as optimizer])

; Remove duplicates
(let [dictionary [{:text "TO BE ANNOTATED" :id "1"}
                  {:text "TO BE ANNOTATED"}]]
  (optimizer/optimize dictionary))
=> ({:text "TO BE ANNOTATED", :id "1"})

; Merge synonyms
(let [dictionary [{:text "TO BE ANNOTATED" :synonyms ["ONE"]}
                  {:text "TO BE ANNOTATED" :synonyms ["TWO"]}]]
  (optimizer/optimize dictionary))
=> ({:text "TO BE ANNOTATED", :synonyms ("TWO" "ONE")})

; Synonyms and text equality check
(let [dictionary [{:text "TO BE ANNOTATED" :synonyms ["TO BE ANNOTATED"]}]]
  (optimizer/optimize dictionary))
=> ({:text "TO BE ANNOTATED", :synonyms ["TO BE ANNOTATED"]})

; Can't be merged because of differences in text analysis
(let [dictionary [{:text "TO BE ANNOTATED" :case-sensitive? true}
                  {:text "TO BE ANNOTATED" :case-sensitive? false}]]
  (optimizer/optimize dictionary))
=> ({:text "TO BE ANNOTATED", :case-sensitive? true} {:text "TO BE ANNOTATED", :case-sensitive? false})
```

## Annotation merger

Only annotations of the same type are merged.

Handled cases:
- Duplicate annotations
- Nested annotations

Examples:
```clojure
(require '[beagle.annotation-merger :as merger])

(let [dictionary [{:text "TEST"}
                  {:text "This TEST is"}]
      annotator (phrases/annotator dictionary)
      annotations (annotator "This TEST is")]
  (println "Annotations: " annotations)
  (merger/merge-same-type-annotations annotations))
Annotations:  ({:text TEST, :type PHRASE, :dict-entry-id 0, :meta {}, :begin-offset 5, :end-offset 9} {:text This TEST is, :type PHRASE, :dict-entry-id 1, :meta {}, :begin-offset 0, :end-offset 12})
=> ({:text "This TEST is", :type "PHRASE", :dict-entry-id "1", :meta {}, :begin-offset 0, :end-offset 12})
```

## License

Copyright &copy; 2019 [TokenMill UAB](http://www.tokenmill.lt).

Distributed under the The Apache License, Version 2.0.
