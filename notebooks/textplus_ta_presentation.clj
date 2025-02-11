^{:nextjournal.clerk/visibility {:code :hide} :nextjournal.clerk/toc true}
(ns textplus-ta-presentation
  (:require
   [julesratte.wikidata :as wd]
   [julesratte.wikidata.properties :refer [wdt]]
   [nextjournal.clerk :as clerk]
   [zdl.wikidata :refer [wd-a]]
   [clojure.java.io :as io]))

;; # Wikidata-Kooperation des DWDS
;;
;; > Wikidata is a free and open knowledge base that **can be read and edited by
;; both humans and machines**. – https://wikidata.org/
;;
;; Das [ZDL](https://www.zdl.org/) hat in seinem Projektantrag ein
;; Arbeitspaket zur Dissemination seiner Ergebnisse im Austausch und
;; in Zusammenarbeit mit Community-Projekten. Ursprünglich wurde hier
;; an Projekte wie [Wiktionary](https://de.wiktionary.org/)
;; oder [OmegaWiki](http://www.omegawiki.org/) gedacht. Im Oktober
;; 2019, kurz nach Projektstart, fand in Berlin allerdings
;; die [WikidataCon](https://www.wikidata.org/wiki/Wikidata:WikidataCon_2019)
;; zum Thema *Languages and Wikidata* statt. Dort gab es erste
;; Gespräche mit der Community und der Wikimedia Foundation zu einer
;; möglichen Zusammenarbeit mit WikiData. Im Herbst 2022 spendete
;; das [DWDS](https://www.dwds.de/) als gegenwartssprachliche
;; Komponente des ZDL erstmals Daten in Form eines automatisierten Imports
;; an Wikidata.
;;

;; ## Wikidata Lexicographical Data – Model
;;
;; Wikidata modelliert Wissen in seiner Datenbasis in Form von Aussagen, ähnlich
;; wie in RDF-basierten Wissengraphen aus dem Kontext des Semantic Web. Aussagen
;; und das Schema, dem diese Aussagen genügen sollen, werden in derselben
;; Datenbasis entwickelt und gepflegt. Das Datenmodell für lexikographische
;; Informationen in Wikidata lehnt sich dabei an das
;; [Lexicon Model for Ontologies (lemon)](https://lemon-model.net/) an und ist
;; durch die Community erweiterbar.

^{::clerk/visibility {:code :hide}}
(->>
 (slurp (io/file "notebooks/Lexeme_data_model.svg"))
 (clerk/html {::clerk/width :wide}))


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

;;;; ## Nutzung der Datenbasis – Abfragen via SPARQL
;;
;; Wikidata bietet einen Abfragedienst – den [Wikidata Query
;; Service](https://query.wikidata.org/), über den mittels SPARQL Abfragen an
;; die Wissensbasis gestellt werden können.

;;
;; ### Sprachen in Wikidata
;;
;; Infolge des Imports ist Deutsch eine der am umfrangreichsten repräsentierten
;; Sprachen in Wikidata.

^{::clerk/viewer clerk/table}
(->>
 (wd/query
  '{:select   [?language [(count :*) ?lexemes]]
    :where    [[_ :dct/language ?language]]
    :group-by [?language]
    :order-by [(desc ?lexemes)]})
 (map (juxt (comp wd/label :language)
            (comp clerk/html wd-a :language)
            :lexemes))
 (cons ["Sprache" "WD-ID" "Lexeme"])
 (clerk/use-headers))

;; ### Informationen zu deutschen Lexemen in Wikidata

;; Da das Schema ebenfalls in Wikidata enthalten ist und über den
;; Abfragedienst zur Verfügung steht, können wir auch das Schema per
;; SPARQL untersuchen. Wir ermitteln zunächst alle Eigenschaften, die
;; Lexemen zugewiesen werden können. Für die ermittelten Eigenschaften
;; fragen wir dann jeweils die Anzahl der Entitäten/Lexeme ab, denen
;; Werte für die Eigenschaft zugewiesen wurden:

^{::clerk/visibility {:result :hide}}
(def german-lang
  (wd/entity "German"))

^{::clerk/visibility {:result :hide}}
(def lexeme-property-type
  (wd/entity "Wikidata property for lexemes"))

^{::clerk/visibility {:result :hide}}
(def lexeme-properties
  (->>
   `{:select   [?prop]
     :where    [[?prop ~(wdt :instance-of) ~lexeme-property-type]]
     :order-by [(asc ?prop)]}
   (wd/query)
   (into #{} (map #(keyword "wdt" (name (:prop %)))))))

^{::clerk/visibility {:result :hide}}
(def lexeme-property-counts
  (->>
   (partition-all 25 lexeme-properties)
   (mapcat
    #(wd/query
      `{:select [?property ?lexemes]
        :where  [~(->>
                   `[[:where {:select [[~prop ?property] [(count ?l) ?lexemes]]
                              :where  [[?l ~prop _]
                                       [?l :dct/language ~german-lang]]}]]
                   (for [prop %])
                   (into [:union]))]}))
   (filter (comp pos? :lexemes))
   (sort-by :lexemes #(compare %2 %1))))

^{::clerk/viewer clerk/table}
(->>
 lexeme-property-counts
 (map (juxt (comp clerk/html wd-a :property) (comp wd/label :property) :lexemes))
 (cons ["WD-ID" "Eigenschaft" "dt. Lexeme"])
 (clerk/use-headers))

;; ## Abgleich verschiedener Wörterbuchbestände
;;
;; Da eine große Zahl an Lexemen in Wikidata über externe Identifikatoren
;; verfügen, lassen sich zum Beispiele Abgleiche zwischen verschiedenen
;; Wörterbuchbeständen realisieren.

^{::clerk/viewer clerk/table}
(->>
 (wd/query
  `{:select [?lemma ?dwds ?duden ?dwb ?lexeme]
    :where  [[?lexeme :wikibase/lemma ?lemma]
             [?lexeme ~(wdt :dwds-lemma-id) ?dwds]
             [?lexeme ~(wdt :duden-lexeme-id) ?duden]
             [?lexeme ~(wdt :dwb-lemma-id) ?dwb]]
    :limit  20})
 (map (juxt (comp clerk/html wd-a :lexeme) :lemma :dwds :duden :dwb))
 (cons ["WD-ID" "Lemma" "DWDS" "Duden" "DWB"])
 (clerk/use-headers))

;; ## Statistiken zu Wortklassenverteilungen

^{::clerk/visibility {:result :hide}}
(defn pos-stats
  [ext-id]
  (->>
   (wd/query
    `{:select   [?pos [(count ?lexeme) ?c]]
      :where    [[?lexeme :wikibase/lexicalCategory ?pos]
                 [?lexeme ~(wdt ext-id) _]]
      :group-by [?pos]
      :order-by [(desc ?c)]
      :limit    10})
   (map (juxt (comp clerk/html wd-a :pos) (comp wd/label :pos) :c))
   (cons ["ID" "Wortklasse" "Lexeme"])
   (clerk/use-headers)
   (clerk/table)))

;; ### DWDS vs. Duden

^{::clerk/visibility {:code :hide}}
(clerk/row
 (pos-stats :dwds-lemma-id)
 (pos-stats :duden-lexeme-id))
