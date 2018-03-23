; Everything here is based on the Google Closure lib:
; https://github.com/google/closure-library/blob/master/closure/goog/i18n/numberformat.js
; Reading the code is far more useful than the official API docs unfortunately.
; Reasons for this wrapper:
; - Multiple locale support.
; - Caching the creation of NumberFormat objects.
; - Converting the super Java flavour OO code into something more functional.
(ns i18n.number
 (:require
  goog.i18n.NumberFormat
  goog.i18n.NumberFormat.Format
  goog.i18n.NumberFormatSymbols
  i18n.goog
  [cljs.test :refer-macros [deftest is are]]
  taoensso.timbre
  i18n.locale
  i18n.data))

; Limits displayed digits after the decimal point.
(def default-max-fraction-digits 1)
(def default-min-fraction-digits 0)
(def default-trailing-zeros false)

; When passed nil instead of a number, what string to return?
(def default-nil-string "")
; When passed NaN instead of a number, what string to return?
(def default-nan-string "-")

; PRIVATE API. DO NOT CALL THIS EXTERNALLY. IT IS FAR TOO EASY TO SCREW THIS UP.

(defn nan?
 [n]
 ; http://adripofjavascript.com/blog/drips/the-problem-with-testing-for-nan-in-javascript.html
 (if (number? n)
  (not (== n n))
  false))

(def formats
 {:decimal (.-DECIMAL goog.i18n.NumberFormat.Format)
  :scientific (.-SCIENTIFIC goog.i18n.NumberFormat.Format)
  :percent (.-PERCENT goog.i18n.NumberFormat.Format)
  :currency (.-CURRENCY goog.i18n.NumberFormat.Format)
  :compact-short (.-COMPACT_SHORT goog.i18n.NumberFormat.Format)
  :compact-long (.-COMPACT_LONG goog.i18n.NumberFormat.Format)})
(def default-pattern :decimal)

(def locale->symbols
 (i18n.goog/locale->symbols-fn :number-format-symbols))

(i18n.goog/register-locale-cb!
 #(set! goog.i18n.NumberFormatSymbols (locale->symbols %)))

(defn formatter
 [& {:keys [max-fraction-digits
            min-fraction-digits
            trailing-zeros?]}]
 (let [max-fraction-digits (or max-fraction-digits default-max-fraction-digits)
       min-fraction-digits (or min-fraction-digits default-min-fraction-digits)
       trailing-zeros? (or trailing-zeros? default-trailing-zeros)]
  (i18n.goog/formatter
   #(doto (goog.i18n.NumberFormat. %)
     ; Limits digits after the decimal point.
     (.setMaximumFractionDigits max-fraction-digits)
     (.setMinimumFractionDigits min-fraction-digits)
     ; Enforce trailing zeros when significant figures is set?
     (.setShowTrailingZeros trailing-zeros?))
   formats)))

(def parser formatter)

; PUBLIC API.

(defn -format
 [n & {:keys [locale
              pattern
              min-fraction-digits
              max-fraction-digits
              trailing-zeros?
              nil-string
              nan-string]}]
 {:pre [(or (nil? n) (number? n))
        (or (nil? locale) (string? locale))]
  :post [(string? %)]}
 (let [locale (or locale (-> i18n.data/locales :default :code))
       nil-string (or nil-string default-nil-string)
       nan-string (or nan-string default-nan-string)]
  (cond
   (nil? n) nil-string
   (nan? n) nan-string
   :else
   (do
    (i18n.goog/set-locale! locale)
    (.format
     ((formatter
       :min-fraction-digits min-fraction-digits
       :max-fraction-digits max-fraction-digits
       :trailing-zeros? trailing-zeros?)
      (or pattern default-pattern))
     n)))))
(def format (memoize -format))

(defn -parse
 [s & {:keys [locale pattern]}]
 {:pre [(string? s) (or (nil? locale) (string? locale))]
  :post [(number? %)]}
 (let [locale (or locale (-> i18n.data/locales :default :code))]
  (i18n.goog/set-locale! locale)
  (.parse
   ((parser)
    (or pattern default-pattern))
   s)))
(def parse (memoize -parse))

; TESTS.

(deftest ??format--fraction-digits
 (let [n (/ 1 3)]
  (is (= "0.3" (format n)))
  (is (= "0.3" (format n :max-fraction-digits 1)))
  (is (= "0.33" (format n :max-fraction-digits 2))))

 (let [n 1]
  (is (= "1" (format n)))
  (is (= "1.0" (format n :min-fraction-digits 1))))

 (let [n (/ 1 3)]
  (is
   (= 
    "Min value must be less than max value"
    (try (format n :min-fraction-digits 2 :max-fraction-digits 1)
     (catch js/Error e
      (.-message e)))))))

(deftest ??locale->symbols
 (is (identical? goog.i18n.NumberFormatSymbols_en_AU (locale->symbols "en-AU")))
 (is (identical? goog.i18n.NumberFormatSymbols_en (locale->symbols "asdf"))))

(deftest ??format-parse
 (let [b 1000000000
       tests [["en-IN" "1,00,00,00,000" "1,00,00,00,000.00"]
              ["en-AU" "1,000,000,000" "1,000,000,000.00"]
              ["gl" "1.000.000.000" "1.000.000.000,00"]]
       ; Shuffle to flush out potential memoize bugs.
       tests' (shuffle tests)]
  ; Do all of this twice to flush out potential memoize bugs.
  (dotimes [n 2]
   (taoensso.timbre/debug "Locale number format test run: " (inc n))

   (doseq [[l f p] tests']
    (taoensso.timbre/debug "Checking locale: " l)
    (is (-> b (format :locale l) (= f))
        (str "Failed to format " l " to " f " correctly." (format b :locale l)))
    (is (-> p (parse :locale l) (= b))
        (str "Failed to parse " p " as " l " correctly." (parse p :locale l)))
    (is (-> b (format :locale l) (parse :locale l) (= b))
        (str "Failed to round trip " l "."))))))

(deftest ??parse-examples
 (let [; Some strings that should evaluate to NaN.
       NaNs ["" "banana" "a1" "-" "NaN"]
       parse-me ["1"
                 "1.0"
                 "1,0"
                 "1a"
                 "1,000"
                 "1,00,0"
                 "1,000,000"
                 "1,000.00"
                 "1.000"
                 "1.00.0"
                 "1.000.000"
                 "1.000,00"]
       test-parsing (fn [l es]
                     (is (= (count es) (count parse-me)))
                     (doseq [[e s] (map vector es parse-me)]
                      (is (= e (parse s :locale l)))))]

  ; Empty string cannot parse to a number.
  (doseq [n NaNs]
   (is (nan? (parse n :locale "en"))))

  (test-parsing "en" [1 1 10 1 1000 1000 1000000 1000 1 1 1 1])

  (test-parsing "en-IN" [1 1 10 1 1000 1000 1000000 1000 1 1 1 1])

  (test-parsing "gl" [1 10 1 1 1 1 1 1 1000 1000 1000000 1000])))

(deftest ??format-examples
 (let [format-me [0
                  0.1
                  1.0
                  1.1
                  1.11
                  1.111
                  1.123
                  1.987
                  1.98
                  1.9
                  1.5
                  -1
                  1
                  10
                  100
                  1000
                  10000
                  1000000
                  1000000000]
       test-formatting (fn [l es]
                        (is (identical? default-nil-string (format nil :locale l)))
                        (is (identical? default-nan-string (format ##NaN :locale l)))
                        (is (= (count es) (count format-me)))
                        (doseq [[e n] (map vector es format-me)]
                         (is (= e (format n :locale l)))))]

  (taoensso.timbre/debug "Test formatting in en locale")
  (test-formatting "en" ["0" "0.1" "1" "1.1" "1.1" "1.1" "1.1" "2" "2" "1.9" "1.5" "-1" "1" "10" "100" "1,000" "10,000" "1,000,000" "1,000,000,000"])

  (taoensso.timbre/debug "Test formatting in en-IN locale")
  (test-formatting "en-IN" ["0" "0.1" "1" "1.1" "1.1" "1.1" "1.1" "2" "2" "1.9" "1.5" "-1" "1" "10" "100" "1,000" "10,000" "10,00,000" "1,00,00,00,000"])

  (taoensso.timbre/debug "Test formatting in gl locale")
  (test-formatting "gl" ["0" "0,1" "1" "1,1" "1,1" "1,1" "1,1" "2" "2" "1,9" "1,5" "-1" "1" "10" "100" "1.000" "10.000" "1.000.000" "1.000.000.000"])))
