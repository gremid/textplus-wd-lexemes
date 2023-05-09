^{:nextjournal.clerk/visibility {:code :hide}}
(ns textplus-ta-presentation
  (:require
   [mundaneum.properties :refer [wdt]]
   [mundaneum.query :as wdq]
   [nextjournal.clerk :as clerk]
   [zdl.wikidata :refer [wd-a]]
   [clojure.java.io :as io]))

;; # Wikidata-Kooperation des DWDS
;;
;; > Wikidata is a free and open knowledge base that **can be read and edited by
;; both humans and machines**. – https://wikidata.org/
;;
;; Das [ZDL](https://www.zdl.org/) hat in seinem Projektantrag ein Arbeitspaket
;; zur Dissemination seiner Ergebnisse im Austausch und in Zusammenarbeit mit
;; Community-Projekten. Ursprünglich wurde hier an Projekte
;; wie [Wiktionary](https://de.wiktionary.org/)
;; oder [OmegaWiki](http://www.omegawiki.org/) gedacht. Im Oktober 2019 fand in
;; Berlin dann
;; die [WikidataCon](https://www.wikidata.org/wiki/Wikidata:WikidataCon_2019)
;; zum Thema *languages and Wikidata* statt. Hier gab es erste Gespräche mit der
;; Community und der Wikimedia Foundation zu einer möglichen Zusammenarbeit.
;;
;; Im Herbst 2022 war es dann soweit. Das [DWDS](https://www.dwds.de/) als
;; gegenwartssprachliche Komponente des ZDL spendete Daten in Form eines
;; automatisierten Imports an Wikidata.
;;
;; ## Der Datenimport
;;
;; Der Import der Daten wurde über die [MediaWiki
;; API](https://www.mediawiki.org/wiki/API:Main_page) bzw.
;; dessen [Wikibase-Erweiterungen](https://www.mediawiki.org/wiki/Wikibase/API/en)
;; durchgeführt.
;;
;; 1. Über einen [Datenexport aller vorhandenen Lexeme](https://dumps.wikimedia.org/wikidatawiki/entities/) in Wikidata wurde ermittelt, welche Lexeme aus dem DWDS importiert werden können.
;; 1. Mittels eines [MediaWiki-Clients](https://github.com/gremid/julesratte) wurde ein Import-Bot entwickelt.
;; 1. Der Bot wurde zunächst gegen eine [containerisierte Wikibase-Testinstanz](https://github.com/zentrum-lexikographie/wikimedia/tree/master/ansible) getestet.
;; 1. Dann durchlief er einen [Wikidata-seitigen QA-Prozess](https://www.wikidata.org/wiki/Wikidata:Requests_for_permissions/Bot/DwdsBot).
;; 1. Nach Akzeptanz des Bots wurden ca. [185.000 Lexeme](https://www.wikidata.org/w/index.php?title=Special%3AContributions&target=DwdsBot&namespace=146&newOnly=1) importiert.
;;
;; ## Sprachen in Wikidata
;;
;; Infolge des Imports ist Deutsch eine der am umfrangreichsten repräsentierten
;; Sprachen in Wikidata.

^{::clerk/visibility {:code :hide}}
(clerk/html {::clerk/width :wide} (slurp (io/file "notebooks/wd-langs.svg")))

;; ## Lexicographical Data – Model
;;
;; Wikidata modelliert Wissen in seiner Datenbasis in Form von Aussagen, ähnlich
;; wie in RDF-basierten Wissengraphen aus dem Kontext des Semantic Web. Aussagen
;; und das Schema, dem diese Aussagen genügen sollen, werden derselben
;; Datenbasis entwickelt und gepflegt. Das Datenmodell für lexikographische
;; Informationen in Wikidata lehnt sich dabei an das
;; [Lexicon Model for Ontologies (lemon)](https://lemon-model.net/) an und ist 
;; durch die Community erweiterbar.

^{::clerk/visibility {:code :hide}}
(->>
 (slurp (io/file "notebooks/Lexeme_data_model.svg"))
 (clerk/html {::clerk/width :wide}))

;; ## Nutzung der Datenbasis – Abfragen via SPARQL
;;
;; Wikidata bietet einen Abfragedienst – den [Wikidata Query
;; Service](https://query.wikidata.org/), über den mittels SPARQL Abfragen an
;; die Wissensbasis gestellt werden können.
;;
;; Die dem obigen *bubble chart* zugrundeliegende Abfrage sieht wie folgt aus:

^{::clerk/viewer clerk/table}
(->>
 (wdq/query
  '{:select   [?language [(count :*) ?lexemes]]
    :where    [[_ :dct/language ?language]]
    :group-by [?language]
    :order-by [(desc ?lexemes)]})
 (map (juxt (comp wdq/label :language)
            (comp clerk/html wd-a :language)
            :lexemes))
 (cons ["Sprache" "WD-ID" "Lexeme"])
 (clerk/use-headers))

;; ## Informationen zu deutschen Lexemen in Wikidata

;; Da das Schema ebenfalls in Wikidata enthalten ist und über den Abfragedienst
;; zur Verfügung steht, können wir auch das Schema per SPARQL untersuchen. Alle
;; Eigenschaften, die Lexemen zugewiesen werden können:

(def lexeme-properties
  (->>
   `{:select   [?prop]
     :where    [[?prop ~(wdt :instance-of) ~(wdq/entity "Wikidata property for lexemes")]]
     :order-by [(asc ?prop)]}
   (wdq/query)
   (into #{} (map #(keyword "wdt" (name (:prop %)))))))

;; Anzahl der Eigenschaften:

(count lexeme-properties)

;; Für die ermittelten Eigenschaften fragen wir jeweils die Anzahl der Entitäten/Lexeme ab, denen Werte für die Eigenschaft zugewiesen wurden:

^{::clerk/visibility {:result :hide}}
(def german-lang
  (wdq/entity "German"))

^{::clerk/viewer clerk/table}
(->>
 (wdq/query
  `{:select [?d ?c]
    :where  [~(->>
               `[[:where {:select [[(count ?l) ?c] [~prop ?d]]
                          :where  [[?l ~prop _]
                                   [?l :dct/language ~german-lang]]}]]
               (for [prop props])
               (into [:union]))]})
 (for [props (partition-all 25 lexeme-properties)])
 (flatten)
 (filter (comp pos? :c))
 (map (juxt (comp clerk/html wd-a :d) (comp wdq/label :d) :c))
 (sort-by #(nth % 2) #(compare %2 %1))
 (cons ["WD-ID" "Eigenschaft" "dt. Lexeme"])
 (clerk/use-headers))

;; ## Abgleich verschiedener Wörterbuchbestände
;;
;; Da eine große Zahl an Lexemen in Wikidata über externe Identifikatoren
;; verfügen, lassen sich zum Beispiele Abgleiche zwischen verschiedenen
;; Wörterbuchbeständen realisieren.

^{::clerk/viewer clerk/table}
(->>
 (wdq/query
  `{:select [?lemma ?dwds ?duden ?dwb ?lexeme]
    :where  [[?lexeme :wikibase/lemma ?lemma]
             [?lexeme ~(wdt :DWDS-lemma-ID) ?dwds]
             [?lexeme ~(wdt :Duden-ID) ?duden]
             [?lexeme ~(wdt :DWB-lemma-ID) ?dwb]]
    :limit  20})
 (map (juxt (comp clerk/html wd-a :lexeme) :lemma :dwds :duden :dwb))
 (cons ["WD-ID" "Lemma" "DWDS" "Duden" "DWB"])
 (clerk/use-headers))

;; ## Statistiken zu Wortklassenverteilungen

^{::clerk/visibility {:result :hide}}
(defn pos-stats
  [ext-id]
  (wdq/query
   `{:select   [?pos [(count ?lexeme) ?c]]
     :where    [[?lexeme :wikibase/lexicalCategory ?pos]
              [?lexeme ~(wdt ext-id) _]]
     :group-by [?pos]
     :order-by [(desc ?c)]
     :limit    10}))

(clerk/row
 (->>
  (pos-stats :elexiko-ID)
  (map (juxt (comp clerk/html wd-a :pos) (comp wdq/label :pos) :c))
  (cons ["W-ID" "elexiko-Wortklasse" "Lexeme"])
  (clerk/use-headers)
  (clerk/table))
 (->>
  (pos-stats :DWB-lemma-ID)
  (map (juxt (comp clerk/html wd-a :pos) (comp wdq/label :pos) :c))
  (cons ["W-ID" "DWB-Wortklasse" "Lexeme"])
  (clerk/use-headers)
  (clerk/table)))
