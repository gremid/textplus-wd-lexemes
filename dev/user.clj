(ns user
  (:require [nextjournal.clerk :as clerk]
            [clojure.string :as str]))

(comment
  (clerk/serve! {:watch-paths ["notebooks" "src"]
                 :browse?     true})
  (clerk/show! "notebooks/textplus_ta_presentation.clj")
  (clerk/build! {:paths ["notebooks/textplus_ta_presentation.clj"]}))
