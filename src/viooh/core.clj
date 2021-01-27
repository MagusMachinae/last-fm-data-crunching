(ns viooh.core
  (:require [clojure-csv.core :as csv]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [tick.alpha.api :as tick]
            [clojure.edn :as edn]
            [clojure.core.reducers :as r]))

(def str-test  "user_000639 \t 2009-04-08T01:57:47Z \t MBID \t The Dogs D'Amour \t MBID \t Fall in Love Again?
      user_000639 \t 2009-04-08T01:53:56Z \t MBID \t The Dogs D'Amour \t MBID \t Wait Until I'm Dead")

(def keys-vector ['user 'time-stamp 'artist-id 'artst-name 'track-id 'track-name])

(def answer '(["Heartless" 1297] ["Love Lockdown" 1295] ["Pinocchio Story (Freestyle Live From Singapore)" 1292] ["Say You Will" 1291] ["Welcome To Heartbreak (Feat. Kid Cudi)" 1291] ["See You In My Nightmares" 1291] ["Amazing (Feat. Young Jeezy)" 1289] ["Paranoid (Feat. Mr. Hudson)" 1289] ["Coldest Winter" 1285] ["Bad News" 1274]))

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

(defn generate-user-sessions!
  [user-vector]
  (pmap (fn [user] (into-sessions! (str "resources/raw-hash-maps/" user ".edn"))) user-vector))

(defn collect-session
  "concatenates sessions from file"
  [user-vector]
  (apply concat
         (pmap (fn [user] (->> (str "resources/sessions/" user "_sessions.edn")
                               (io/reader)
                               (line-seq)
                               (map read-string)))
               user-vector)))

(defn count-occurences
  "returns a map of songs mapped to the total number number of times they are played"
  [coll]
  (r/fold
    (r/monoid #(merge-with + %1 %2) (constantly {}))
    (fn [m [k cnt]] (assoc m k (+ cnt (get m k 0))))
    (r/map #(vector % 1) (into [] coll))))

(count-occurences   ["foo" "foo" "bar"])

(defn top-by [n k coll]
     (reduce
      (fn [top x]
        (let [top (conj top x)]
          (if (> (count top) n)
            (disj top (first top))
            top)))
      (sorted-set-by #(< (k %1) (k %2))) coll))

(defn top-by-f [n k coll]
     (r/fold
      (fn [top x]
        (let [top (conj top x)]
          (if (> (count top) n)
            (disj top (first top))
            top)))
      (sorted-set-by #(< (k %1) (k %2))) coll))



(defn concat-tracks
  "gets all tracks from the sessions supplied in coll as a single coll."
  [coll]
  (->> coll
   (pmap (fn [session] (get session :session-data)))
   (apply concat)
   (pmap (fn [session-data] (get session-data :track-name)))))

(defn top-ten-tracks
  "sorts the occurences by value"
  [coll]
  (take 10 (sort-by second > (count-occurences (concat-tracks coll)))))

(defn calculate-answer
  "calls the necessary functions when given the user-vector to calculate the result"
  [coll]
  (top-ten-tracks (top-by 50 :play-count (collect-session coll))))

(defn prune-answers
  "prepares answer for printingto tsv."
  [coll]
   (map (fn [[k v]]  [k (str "Plays:" (str v))]) coll))

(defn answer->tsv!
  "takes a coll of the top ten answers and writes it out to a tsv file"
  [coll]
  (spit "answer.tsv" (csv/write-csv (prune-answers coll) :delimiter \tab)))

(comment
 (data->edn! "resources/song-data.tsv")
 (generate-user-sessions! user-vector)
 (answer-tsv! (calculate-answer user-vector))
 (answer->tsv! answer)


 (into-sessions! "resources/raw-hash-maps/user_000001.edn")

 (pmap keyword user-vector)
 (slurp "top50.edn")
 (map read-string (line-seq (io/reader "resources/raw-hash-maps/user_000001.edn")))
 (map read-string
   (line-seq (io/reader (str "resources/sessions/"
                             user
                             "_sessions.edn"))))

 (spit "top50.edn "(top-by 50 :play-count (apply concat
                                            (pmap (fn [user] (map read-string
                                                                  (line-seq (io/reader (str "resources/sessions/"
                                                                                            user
                                                                                            "_sessions.edn"))))) user-vector))))
(time (top-by 50 :play-count (apply concat
                                           (pmap (fn [user] (map read-string
                                                                 (line-seq (io/reader (str "resources/sessions/"
                                                                                           user
                                                                                           "_sessions.edn"))))) user-vector))))

(top-by-f 50 :play-count (into [] (apply concat
                                    (pmap (fn [user] (map read-string
                                                          (line-seq (io/reader (str "resources/sessions/"
                                                                                user
                                                                                "_sessions.edn")))))
                                          user-vector))))

 (concat-tracks (read-string (slurp "top50.edn")))



 (take 10 (sort-by second > (count-occurences (->> (read-string (slurp "top50.edn"))
                                                   (pmap (fn [session] (get session :session-data)))
                                                   (apply concat)
                                                   (pmap (fn [session-data] (get session-data :track-name)))))))

 (spit "foo.edn"  (apply concat
                    (pmap (fn [user] (->> (str "resources/sessions/" user "_sessions.edn")
                                          (io/reader)
                                          (line-seq)
                                          (map read-string)))
                          user-vector)))

 (for [x (csv/parse-csv str-test :delimiter \tab)]
   ((fn [coll] (dissoc coll :artist-id :track-id))
    (zipmap (map keyword keys-vector) (map str/trim x))))

 (session-stepper (for [x (csv/parse-csv str-test :delimiter \tab)]
                    (dissoc (zipmap (map keyword keys-vector)
                                    (map str/trim x))
                            :artist-id :track-id)))

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
  (tick/instant "2009-04-08T01:57:47Z"))
 (some? (first [nil]))
 (spit "target/foo.edn" '("foo" "bar" "baz")))
