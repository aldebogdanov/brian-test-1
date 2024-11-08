(ns brian-test-1.core
  (:require
    [hyperfiddle.rcf :as rcf]
    [malli.core :as malli]
    [malli.instrument :as mi])
  (:import
    (java.time
      DayOfWeek
      OffsetDateTime
      ZoneId
      ZonedDateTime)
    (java.time.chrono
      IsoChronology)
    (java.time.format
      DateTimeFormatter
      DateTimeParseException)
    (java.time.temporal
      ChronoField
      WeekFields)))


(def common-formatter  (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ssXXX"))

(def week-formatter    (DateTimeFormatter/ofPattern "Y-'W'ww-e'T'HH:mm:ssXXX"))

(def ordinal-formatter (DateTimeFormatter/ofPattern "yyyy-DDD'T'HH:mm:ssXXX"))


(defn- str->str+era
  [dt-str]
  (if-let [is-bc?   (= \- (first dt-str))]
    (let [year-str (apply subs dt-str (if is-bc? [1 5] [0 4]))
          year     (cond-> (Long/parseLong year-str)
                     is-bc? inc)
          rest-dt  (subs dt-str (if is-bc? 5 4))]
      [(str year rest-dt) 0])
    [dt-str 1]))


(tests
  (str->str+era "2024-11-08T19:20+03:00") := ["2024-11-08T19:20+03:00" 1]
  (str->str+era "-2024-11-08T19:20+03:00") := ["2025-11-08T19:20+03:00" 0])


(defn- generate-spec
  [^DateTimeFormatter formatter]
  [:fn (fn [dt-str]
         (let [[s _] (str->str+era dt-str)]
           (try (.parse formatter s)
                true
                (catch DateTimeParseException _ false))))])


(def ISODateTimeString
  [:and
   :string
   [:or
    (generate-spec common-formatter)
    (generate-spec week-formatter)
    (generate-spec ordinal-formatter)]])


(malli/=> parse-dt-string [:-> ISODateTimeString [:fn #(instance? OffsetDateTime %)]])


(defn- parse-dt-string
  [date-str]
  (let [[s era] (str->str+era date-str)
        parsed  (try (OffsetDateTime/parse s common-formatter)
                     (catch DateTimeParseException _
                       (try (OffsetDateTime/parse s week-formatter)
                            (catch DateTimeParseException _
                              (OffsetDateTime/parse s ordinal-formatter)))))]
    (.with parsed ChronoField/ERA (.eraOf IsoChronology/INSTANCE era))))


(malli/=> get-week-of-year [:-> ISODateTimeString [:int {:min 1 :max 53}]])


(defn get-week-of-year
  [s]
  (let [target-zone      (ZoneId/of "Europe/Berlin")
        ^ZonedDateTime
        zoned-date-time  (-> s parse-dt-string (.atZoneSameInstant target-zone))
        week-fields      (WeekFields/of DayOfWeek/MONDAY 4)]
    ;; (println zoned-date-time)
    (.get zoned-date-time (.weekOfWeekBasedYear week-fields))))


(get-week-of-year "1977-05-25T10:00:00-07:00")

(get-week-of-year "2000-W01-1T00:00:00+03:00")


(comment
  (mi/instrument!)
  (rcf/enable!))


(rcf/tests
  ;; ===============================
  ;; ✅ 1. Valid Test Cases (Common, Week-Based, and Ordinal Dates with Time)
  ;; ===============================

  ;; VC-01: Apollo 11 Moon Landing (July 20, 1969)
  (get-week-of-year "1969-07-20T20:17:40-04:00") := 30

  ;; VC-02: 9/11 Attacks (September 11, 2001)
  (get-week-of-year "2001-09-11T08:46:00-05:00") := 37

  ;; VC-03: Fall of the Berlin Wall (November 9, 1989)
  (get-week-of-year "1989-W44-6T23:59:59+00:00") := 45

  ;; VC-04: Star Wars Original Release Date (May 25, 1977)
  (get-week-of-year "1977-05-25T10:00:00-07:00") := 21

  ;; VC-05: Back to the Future Date (October 21, 2015)
  (get-week-of-year "2015-W42-3T07:00:00-07:00") := 42

  ;; VC-06: Assassination of Julius Caesar (March 15, 44 BC)
  (get-week-of-year "-0044-074T12:00:00+00:00") := 11


  ;; ===============================
  ;; ❌ 2. Invalid Test Cases (Common, Week-Based, and Ordinal Dates with Time)
  ;; ===============================

  ;; IC-01: Invalid Month (13) in Historical Common Date
  (get-week-of-year "2001-13-01T00:00:00+01:00") :throws Exception

  ;; IC-02: Invalid Day (February 30th) in Historical Common Date
  (get-week-of-year "2001-02-30T12:00:00+01:00") :throws Exception

  ;; IC-03: Missing Timezone in Ancient Common Date
  (get-week-of-year "-0044-03-15T12:00:00") :throws Exception

  ;; IC-04: Invalid Week Number (54) in Sci-fi Week-Based Date
  (get-week-of-year "1977-W54-3T10:00:00-07:00") :throws Exception

  ;; IC-05: Invalid Day of Week (8) in Sci-fi Week-Based Date
  (get-week-of-year "1977-W21-8T10:00:00-07:00") :throws Exception

  ;; IC-06: Incorrect Format (Missing Separators) in Ancient Week-Based Date
  (get-week-of-year "-0044W11D6T12:00:00+00:00") :throws Exception

  ;; IC-07: Invalid Ordinal Day (000) in Historical Ordinal Date
  (get-week-of-year "2001-000T00:00:00+01:00") :throws Exception

  ;; IC-08: Invalid Ordinal Day (367) in Sci-fi Ordinal Date (Non-leap Year)
  (get-week-of-year "2015-367T07:00:00-07:00") :throws Exception

  ;; IC-09: Missing Time Component in Ancient Ordinal Date
  (get-week-of-year "-0044-075T12:00:00") :throws Exception


  ;; ===============================
  ;; 🧨 3. Edge Cases (Common, Week-Based, and Ordinal Dates with Time)
  ;; ===============================

  ;; EC-01: End of a Year with 53 Weeks (December 31, 2015)
  (get-week-of-year "2015-12-31T23:59:59-05:00") := 53

  ;; EC-02: Early January Date Shifted to Last ISO Week (January 1, 2016)
  (get-week-of-year "2016-01-01T00:00:00+10:00") := 53

  ;; EC-03: Last Day of a 53-Week Year in Sydney (December 31, 2009)
  (get-week-of-year "2009-12-31T23:59:59+10:00") := 53

  ;; EC-04: First Day of the 53rd Week in a 53-Week Year (December 28, 2020)
  (get-week-of-year "2020-W53-1T00:00:00+00:00") := 53

  ;; EC-05: Last Day of a 53-Week Year During Daylight Saving Time (December 31, 2024)
  (get-week-of-year "2024-W53-7T23:59:59+02:00") := 53

  ;; EC-06: Testing Maximum Week Number for a Non-53-Week Year (Week 53 in 2023)
  (get-week-of-year "2023-W53-1T00:00:00+00:00") :throws Exception

  ;; EC-07: Ordinal Date During Daylight Saving Time (August 17, 2024)
  (get-week-of-year "2024-250T14:45:00+02:00") := 33

  ;; EC-08: Ordinal Date in a Leap Year (December 31, 2020)
  (get-week-of-year "2020-366T23:59:59+02:00") := 53

  ;; EC-09: Ordinal Date Converted from Tokyo Timezone (March 4, 2004)
  (get-week-of-year "2004-003T15:30:00+09:00") := 1)
