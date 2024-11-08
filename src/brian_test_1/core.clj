(ns brian-test-1.core
  (:require
    [hyperfiddle.rcf :as rcf]
    [malli.core :as malli]
    [malli.instrument :as mi])
  (:import
    (java.time
      DayOfWeek
      ZoneId
      ZonedDateTime)
    (java.time.format
      DateTimeFormatter
      DateTimeFormatterBuilder
      DateTimeParseException)
    (java.time.temporal
      ChronoField
      TemporalAccessor
      WeekFields)))


;; Formatters

(def ^:private week-fields
  (WeekFields/of DayOfWeek/MONDAY 4))


(def common-formatter
  "Common ISO datetime string formatter"
  DateTimeFormatter/ISO_OFFSET_DATE_TIME)


(def week-formatter
  "Formatter for ISO week datetime string"
  (-> (DateTimeFormatterBuilder.)
      (.appendValue (.weekBasedYear week-fields) 4)
      (.appendLiteral "-W")
      (.appendValue (.weekOfWeekBasedYear week-fields) 2)
      (.appendLiteral "-")
      (.appendValue ChronoField/DAY_OF_WEEK)
      (.appendLiteral "T")
      (.appendPattern "HH:mm:ssXXX")
      (.toFormatter)))


(def ordinal-formatter
  "Formatter for ISO ordinal datetime string"
  (DateTimeFormatter/ofPattern "yyyy-DDD'T'HH:mm:ssXXX"))


;; Specs

(defn- generate-spec
  [^DateTimeFormatter formatter]
  [:fn (fn [s]
         (try (.parse formatter s)
              true
              (catch DateTimeParseException _ false)))])


(def ^:private CommonString  (generate-spec common-formatter))
(def ^:private WeekString    (generate-spec week-formatter))
(def ^:private OrdinalString (generate-spec ordinal-formatter))


(def ISODateTimeString
  "Malli schema for datetime string"
  [:and
   :string
   [:or
    CommonString
    WeekString
    OrdinalString]])


;; Logic


(malli/=> parse-dt-string [:-> ISODateTimeString [:fn #(instance? TemporalAccessor %)]])


(defn- parse-dt-string
  [s]
  (try (.parse common-formatter s)
       (catch DateTimeParseException _
         (try (.parse week-formatter s)
              (catch DateTimeParseException _
                (.parse ordinal-formatter s))))))


(malli/=> get-week-of-year [:-> ISODateTimeString [:int {:min 1 :max 53}]])


(defn ^{:arglists '([datetime-string])
        :added "0.1.0"
        :doc "
Returns week number in `Europe/Berlin` timezone for corresponding datetime string.
Works with respect of ISO weeks: week starts from Monday and have at least four days in it.
For more information check ``https://en.wikipedia.org/wiki/ISO_8601#Week_dates`.

Input string could be in three ISO formats:

* common ISO datetime string with offset: \"2024-11-08T12:40:33+03:00\"
* ISO week date with time and offset: \"2024-32T08:00:00-07:00\"
* ISO ordinal date with offset: \"2024-001T12:30:32+05:30\"

Returns java.lang.Long value from 1 to 53 (including).

Throws ExceptionInfo on wrong input.

Supports only A.C. dates.
"} get-week-of-year
  [datetime-string]
  (let [^ZoneId
        target-zone (ZoneId/of "Europe/Berlin")
        ^TemporalAccessor
        parsed (parse-dt-string datetime-string)
        ^ZonedDateTime
        zoned-date-time (-> parsed
                            ZonedDateTime/from
                            (.withZoneSameInstant target-zone))]
    (.get zoned-date-time (.weekOfWeekBasedYear week-fields))))


;; Dev


(comment
  (set! *warn-on-reflection* true)
  (mi/instrument!)
  (rcf/enable!))


;; Tests


(rcf/tests
  ;; ===============================
  ;; ✅ 1. Valid Test Cases (Common, Week-Based, and Ordinal Dates with Time)
  ;; ===============================

  ;; VC-01: Apollo 11 Moon Landing (July 20, 1969)
  (get-week-of-year "1969-07-20T20:17:40-04:00") := 30

  ;; VC-02: 9/11 Attacks (September 11, 2001)
  (get-week-of-year "2001-09-11T08:46:00-05:00") := 37

  ;; VC-03: Fall of the Berlin Wall (November 9, 1989)
  (get-week-of-year "1989-W44-6T23:59:59+00:00") := 44

  ;; VC-04: Star Wars Original Release Date (May 25, 1977)
  (get-week-of-year "1977-05-25T10:00:00-07:00") := 21

  ;; VC-05: Back to the Future Date (October 21, 2015)
  (get-week-of-year "2015-W42-3T07:00:00-07:00") := 42


  ;; ===============================
  ;; ❌ 2. Invalid Test Cases (Common, Week-Based, and Ordinal Dates with Time)
  ;; ===============================

  ;; IC-01: Invalid Month (13) in Historical Common Date
  (get-week-of-year "2001-13-01T00:00:00+01:00") :throws Exception

  ;; IC-02: Invalid Day (February 30th) in Historical Common Date
  (get-week-of-year "2001-02-30T12:00:00+01:00") :throws Exception

  ;; IC-04: Invalid Week Number (54) in Sci-fi Week-Based Date
  (get-week-of-year "1977-W54-3T10:00:00-07:00") :throws Exception

  ;; IC-05: Invalid Day of Week (8) in Sci-fi Week-Based Date
  (get-week-of-year "1977-W21-8T10:00:00-07:00") :throws Exception

  ;; IC-07: Invalid Ordinal Day (000) in Historical Ordinal Date
  (get-week-of-year "2001-000T00:00:00+01:00") :throws Exception

  ;; IC-08: Invalid Ordinal Day (367) in Sci-fi Ordinal Date (Non-leap Year)
  (get-week-of-year "2015-367T07:00:00-07:00") :throws Exception

  ;; IC-09: Invalid Input Type
  (get-week-of-year 2024) :throws Exception

  ;; IC-09: Nil Input
  (get-week-of-year nil) :throws Exception

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
  (get-week-of-year "2024-W53-7T23:59:59+02:00") :throws Exception

  ;; EC-06: Testing Maximum Week Number for a Non-53-Week Year (Week 53 in 2023)
  (get-week-of-year "2023-W53-1T00:00:00+00:00") :throws Exception

  ;; EC-07: Ordinal Date During Daylight Saving Time (August 17, 2024)
  (get-week-of-year "2024-250T14:45:00+02:00") := 36

  ;; EC-08: Ordinal Date in a Leap Year (December 31, 2020)
  (get-week-of-year "2020-366T23:59:59+02:00") := 53

  ;; EC-09: Ordinal Date Converted from Tokyo Timezone (March 4, 2004)
  (get-week-of-year "2004-003T15:30:00+09:00") := 1)
