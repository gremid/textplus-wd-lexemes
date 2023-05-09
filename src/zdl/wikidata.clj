(ns zdl.wikidata
  (:require
   [mundaneum.query :refer [default-language query *default-language*]]
   [clojure.string :as str]))

(def lexeme*
  "Memoized implementation of language-aware entity lookup."
  (memoize
   (fn [lang lemma criteria]
     (-> `{:select [?lexeme]
           :where  [[?lexeme :a :ontolex/LexicalEntry]
                    [?lexeme :wikibase/lemma {~lang ~lemma}]
                    ~@(mapv (fn [[p e]] `[?item ~p ~e])
                            (partition 2 criteria))]
           :limit 1}
         query
         first
         :lexeme))))

(defn lexeme
  "Return a keyword like :wd/Q42 for the most popular WikiData entity that matches `label`."
  [label & criteria]
  (let [[lang label'] (if (map? label)
                        (first label)
                        [(default-language) label])]
    (lexeme* lang label' criteria)))

(defn wd-a
  [kw]
  (if (#{"wd" "wdt"} (namespace kw))
    (let [k    (name kw)
          href (str "https://www.wikidata.org/wiki/"
                    (condp #(str/starts-with? %2 %1) k
                      "P" "Property:"
                      "L" "Lexeme:"
                      "")
                    k)]
      [:a {:href href :title (str kw)} k])
    kw))


(comment
  (binding [*default-language* :de] (lexeme "Zyklop"))
  (wd-a :wd/Q188))
