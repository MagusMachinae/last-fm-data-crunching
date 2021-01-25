(ns viooh.core
  (:require [clojure-csv.core :as csv]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [tick.alpha.api :as tick]
            [clojure.edn :as edn]))

(def str-test  "user_000639 \t 2009-04-08T01:57:47Z \t MBID \t The Dogs D'Amour \t MBID \t Fall in Love Again?
      user_000639 \t 2009-04-08T01:53:56Z \t MBID \t The Dogs D'Amour \t MBID \t Wait Until I'm Dead")

(def keys-vector ['user 'time-stamp 'artist-id 'artst-name 'track-id 'track-name])

(defn data->edn! [src]
  "pulls tsv data into files of hash-maps by user"
  (with-open [reader (io/reader src)]
    (doseq [data (drop 1 (csv/parse-csv reader :delimiter \tab))]
      (let [hash-data (zipmap (map keyword keys-vector) (map str/trim data))]
        (spit (str "resources/raw-hash-maps/" (:user hash-data) ".edn")
              (str (dissoc hash-data :artist-id :track-id) "\n")
              :append true)))))

(def user-vector
  (with-open [reader (io/reader "resources/userid-profile.tsv")]
     (into [] (for [x (take 647 (drop 1 (csv/parse-csv reader :delimiter \tab)))]
                   (first (map str/trim x))))))

(defn same-session?
  "Checks if time-1 is within 20 minutes after time-2"
  [time-1 time-2]
  (if (some? time-2)
    (tick/<= (tick/instant time-1)
             (tick/+ (tick/instant time-2)
                     (tick/new-duration 20 :minutes)))
    false))

(same-session? "2009-04-08T01:57:47Z" "2009-04-08T01:37:47Z")

(defn session-stepper
  "steps through the data from a file, collecting it up into session until it reaches
   a boundary, returning the session and the remainder of the lazy-seq of data"
  [file-stream]
  (loop [play-1 (first file-stream)
         play-2  (first (rest file-stream))
         acc     [play-1]
         remainder (rest (rest file-stream))]
    (if (same-session? (:time-stamp play-1) (:time-stamp play-2));;;
      (recur play-2
        (first remainder)
        (conj acc play-2)
        (rest remainder))
      [acc remainder])))

(defn into-sessions!
  "sorts the hashmaps in a file into sessions"
  [file]
  (with-open [reader (io/reader file)]
    (let [file-stream (map read-string (line-seq reader))]
      (loop [session-num 1
             remainder  (second (session-stepper file-stream))
             session-data (first (session-stepper file-stream))]
        (if (some? (first remainder))
          (do (spit (str "resources/sessions/" (:user (first session-data)) "_sessions.edn")
                    (str {:session-name (str (:user (first session-data)) "session-" session-num)
                          :play-count (count session-data)
                          :session-data session-data} "\n")
                    :append true)
            (recur (inc session-num)
                   (second (session-stepper remainder))
                   (first (session-stepper remainder))))
          (spit (str "resources/sessions/" (:user (first session-data)) "_sessions.edn")
                (str {:session-name (str (:user (first session-data)) "session-" session-num)
                      :play-count (count session-data)
                      :session-data session-data} "\n")
                :append true))))))

(for [user vd/user-vector]
 (into-sessions! (str "resources/raw-hash-maps/" user ".edn")))
(into-sessions! "resources/raw-hash-maps/user_000001.edn")

(defn collect-session [user-vector]
  (loop [reader (io/reader (str "resources/raw-hash-maps/"
                                (first user-vector)
                                ".edn"))
         acc '()]

    (cons acc (line-seq))))

(defn sort!
 "sorts lazy-seq of sessions by play-count, and taking the top 50 and putting them into a single file."
  [user-vector]
  (spit
   (take 50 (sort-by :play-count > (collect-sessions user-vector)))))

(map read-string (line-seq (io/reader "resources/raw-hash-maps/user_000001.edn")))

(comment



         (pmap keyword vd/user-vector)

         (for [x (csv/parse-csv str-test :delimiter \tab)]
           ((fn [coll] (dissoc coll :artist-id :track-id))
            (zipmap (map keyword keys-vector) (map str/trim x))))

         (session-stepper (for [x (csv/parse-csv str-test :delimiter \tab)]
                            (dissoc (zipmap (map keyword keys-vector)
                                            (map str/trim x))
                                    :artist-id :track-id)))

         (for [user vd/user-vector
               song-data (csv/parse-csv)])

 (first (first (session-stepper (map read-string (line-seq (io/reader "resources/raw-hash-maps/user_000001.edn"))))))
 (let [file-stream (map read-string (line-seq (io/reader "resources/raw-hash-maps/user_000001.edn")))]
   (loop [
          session-num 1

          remainder  (second (session-stepper file-stream))
          session-data (first (session-stepper file-stream))]

     (if (some? (first remainder))
       (do (spit (str "resources/sessions/" (:user  (first session-data)) "_sessions.edn")
                 (str {:session-name (str (:user (first session-data)) "session-" session-num)
                       :play-count (count session-data)
                       :session-data session-data} "\n")
                 :append true)
         (recur (inc session-num)
           (second (session-stepper remainder))
           (first (session-stepper remainder))))
       (spit (str "resources/sessions/" (:user (first session-data)) "_sessions.edn")
        (str {:session-name (str (:user (first session-data)) "session-" session-num)
              :play-count (count session-data)
              :session-data session-data} "\n")
        :append true))))
 (zipmap (map keyword keys-vector) '("user_000639"
                                     "2009-04-08T01:57:47Z"
                                     "MBID"
                                     "The Dogs D'Amour"
                                     "MBID"
                                     "Fall in Love Again?"))
 (tick/+
  (tick/instant "2009-04-08T01:57:47Z")))
(some? (first [nil]))
(spit "target/foo.edn" '("foo" "bar" "baz"))
