### Task

* Write a function that given an ISO date string, returns the week of the year in timezone "Europe/Berlin"
* must respect ISO 8601 Week
* no time libraries, only java.*
* can use: malli.*, clojure.test*, hyperfiddle.rcf
* expected output / evaluation criteria:
   - the steps you took to solve it (best to write them down as you do it) / there's no wrongs IMO, it's more about the journey
   - tests / which kind of tests you wrote, how did you come up with the cases
   - the function itself / style
* the whole thing can be a code snippet as long as the dependencies, require and imports are provided
* the steps can be provided separatly
* function and tests should be written and commented as you would in production code**

`(get-week-of-year "2024-11-03T23:00:00Z")`

### Solution

*This story was summarized from ongoing voice memos with help of AI*

* Created GitHub repository with `README.md`, `deps.edn` and `src/brian_test_1/core.clj` files.
* Added dependencies: `metosin/malli`, `com.hyperfiddle/rcf` and `org.clojure/clojure` itself.
* Asked ChatGPT to create RCF test-suite with valid, invalid and edge cases. Fixed where needed.
* Investigated what is ISO week. Learned new things.
* At this moment I imagined approach of manual calculating weeks from date by counting days with maths. I thought that biggest problem will be to correctly process first and last weeks and leap days.
* But in `java.time.` I found that Java out of the box can get a week number of date. That does not violate `java.*` restriction.
* But simultaneously arised a problem that ISO standard have two different date notations like ISO week date and ISO ordinary date. As well as in task description says about ISO weeks I decided that support of ISO week date string must be implemented. As well as ISO ordinary date string also.
* None of Java built-in parsers supports multiple formats in same time, so I decided to use three separated parsers.
* After parsers was created Malli schema for function to use in development. Since Malli instrumentation is switched off in production, I decided to use predicate function that tries to parse input string. If at least one parser succeed, then ok. main point is that `:malli/invalid-input` and `:malli/invalid-output` could be handled in specific way in error handlers to provide comprehensive error data. But as a trade-off in development time we're losing detailed types of exceptions (parsing error, nonexistent date, etc...). So, it's a point to discuss.
* After parsing important not to forget to transform datetime to correct timezone (Europe/Berlin).
* Biggest problem I encountered was how to set `week-parser` to calculate weeks like in ISO specification (Monday first and at least four days in a week). `DateTimeFormatter` for week string parser had to be created via Java interop with docs I do not like much, and GPT was trapped with answers barely related to topic...
* Problem was even not to get the number from final date but to not fail on parsing edge cases with 53th week in original string. Thanks to comprehencive test suite and convenient RCF tests, I realized that out-of-the-box week string parser does not satisfies ISO week spec and starts week from Sunday at least.
* Finally I found the way to parse edge cases without exceptions.
* But new conern arrived. What about BC years? Java parsers do not support negative years in string, but ISO spec does! I found a workaround how manually set era and adjust year (1st BC year is "0000" in string representation), but code starts getting cluttered and moreover I had no any idea how to work with timezones in BC era. So I made a tradeoff to not support BC regarding to strict mention of timezones in task when in BC was only local sun time. Anyway there is a commit with some preparations about ancient times, it is possible to implement if agree how to deal with timezones.
* All tests passing, code cleaned and commented where needed. I started to create this story...




