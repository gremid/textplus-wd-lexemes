(ns zdl.wikidata
  (:require
   [julesratte.wikidata :as wd]
   [clojure.string :as str]))

(def lexeme
  "Memoized implementation of language-aware entity lookup."
  (memoize
   (fn lexeme
     ([lemma]
      (lexeme :de lemma))
     ([lang lemma]
      (lexeme lang lemma []))
     ([lang lemma criteria]
     (-> `{:select [?lexeme]
           :where  [[?lexeme :a :ontolex/LexicalEntry]
                    [?lexeme :wikibase/lemma {~lang ~lemma}]
                    ~@(mapv (fn [[p e]] `[?item ~p ~e])
                            (partition 2 criteria))]
           :limit 1}
         wd/query
         first
         :lexeme)))))

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
  (lexeme "Zyklop")
  (wd-a :wd/Q188))
