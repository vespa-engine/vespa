<!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->

![Vespa logo](https://vespa.ai/assets/vespa-logo-color.png)

# Vespa clients
This part of the Vespa repository got Vespa client implementations for operations like
* deploy
* read/write
* query

<!-- ToDo: illustration -->



## Vespa CLI
The Vespa command-line tool, see the [README](go/README.md).
Use the Vespa CLI to deploy, feed and query a Vespa application,
for local, self-hosted or [Vespa Cloud](https://cloud.vespa.ai/) instances.



## pyvespa
[pyvespa](https://pyvespa.readthedocs.io/) provides a python API to Vespa -
use it to create, modify, deploy and interact with running Vespa instances.
The main pyvespa goal is to allow for faster prototyping
and to facilitate Machine Learning experiments for Vespa applications.



## Vespa FE (fixme: better name and description here)
This is a [work-in-progress javascript app](js/app) for querying a Vespa application.



----

## Misc

<!-- ToDo: move this / demote this somehow -->
### vespa_query_dsl
This lib is used for composing Vespa
[YQL queries](https://docs.vespa.ai/en/reference/query-language-reference.html).
For usage, refer to the [QTest.java](src/test/java/ai/vespa/client/dsl/QTest.java) unit test.

ToDos:
- [ ] support `predicate` (https://docs.vespa.ai/en/predicate-fields.html)
- [ ] support methods for checking positive/negative conditions for specific field
- [X] support order by annotation
- [X] support order by
- [X] support sub operators in contains (sameElement, phrase, near, onear, equiv)
- [X] support group syntax
- [X] support `nonEmpty`
- [X] support `dotProduct`
- [X] support `weightedSet`
- [X] support `wand`
- [X] support `weakAnd`
- [x] support `userInput`
- [x] support `rank`
- [x] support filter annotation
- [X] unit tests
- [X] support other annotations
- [X] handle edge cases (e.g. `Q.b("test").contains("a").build()`)
