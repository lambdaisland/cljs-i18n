# cljs-i18n

Google Closure `goog.i18n` is a wrapper for the Unicode CLDR data `goog.i18n` that provides localisation logic for:

- Datetime/Timezone formatting and parsing
- Number formatting and parsing
- Collation
- Currency formatting
- Plurals

Which is amazing for several reasons:

- We get it "free" in clojure
- Very few l10n libs out there support both bidirectional formatting _and_ parsing
- CLDR is really the only way to get high quality coverage for every language/locale out there
  - Maintained by Unicode
  - ISO standard templating language for format/parse logic (many libs hand-roll their own and then fail to account for certain nuances)
  - Supports literally thousands of locale/country combinations
  - Is updated as cultures/locales evolve over time
  - Available as open source JSON files

Unfortunately, the interface to `goog.i18n` is about as far from idiomatic
clojure as one could possibly get. Most of the behaviour is determined by
fiddling with mutable properties of _both_ global and local objects that are
deeply complected with each other. E.g. the "current locale" is set globally but
configuration like "formatter pattern" is set as a property on a local
`formatter` object.

Natively `goog.i18n` does not expose the ability to work with more than one
locale at a time. Internally it has several mostly undocumented global
properties such as `goog.i18n.NumberFormatSymbols` and
`goog.i18n.DateTimeSymbols` that must be set manually on each locale change.

Presumably Google know what they are doing and so all this mutation and limits
placed on dynamic locale support is for performance reasons or something. Based
on that assumption (I haven't done extensive benchmarking/profiling) I've
memoized and implemented automated tests for as much as I can. As localisation
of a string for a given locale/pattern is totally referentially transparent the
default is to cache aggressively using native cljs `memoize`.

Of course, the aggressive memoization could lead to memory leaks, depending on
what you are doing in your application. It's great if you have a few strings
that are being re-used across the UI, potentially very bad if you have a lot of
unique strings to process.

Each of the core fns in the public API has an unmemoized version prefixed by `-`
so that `parse` is cached while `-parse` is not.

The end result of this library is the ability to do something like this:

```clojure
(parse "1.000.000.000,00" :locale "gl") ; 1000000000 in Galician
(parse "1,000,000,000.00" :locale "en") ; 1000000000 in English
(parse "1,00,00,00,000.00" :locale "en-IN") ; 1000000000 in Indian English

(format 1000000000 :locale "gl") ; "1.000.000.000" in Galician
(format 1000000000 :locale "en") ; "1,000,000,000" in English
(format 1000000000 :locale "en-IN") ; "1,00,00,00,000" in Indian English
```

Additionally this lib provides several things `goog.i18n` is missing that we
need in order to work with an end-user's locale in the browser:

- Extracting the user's preferred locale based on OS/browser/config settings
- Normalizing langcodes format (e.g. `en_US` to `en-US`)
- Extracting a supported langcode from `Accept-Language` HTTP headers

## Supported locales

Everything from `goog.i18n.NumberFormatSymbols` as at 2018-03-23.

From the Google docs:

> * File generated from CLDR ver. 32
> *
> * To reduce the file size (which may cause issues in some JS
> * developing environments), this file will only contain locales
> * that are frequently used by web applications. This is defined as
> * proto/closure_locales_data.txt and will change (most likely addition)
> * over time.  Rest of the data can be found in another file named
> * "numberformatsymbolsext.js", which will be generated at
> * the same time together with this file.

I don't have a script/build process to track what Google Closure supports under
the primary namespace automatically. Also note that there are literally hundreds
of additional locales available in `goog.i18n.NumberFormatSymbolsExt`.

Adding new locales is a simple matter of adding the relevant k/v pair to
`i18n.data/locales`. If a locale you're looking for is missing please feel free
to put a pull request up for inclusion.
