<!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->

# Vespa clients

## Vespa CLI
The Vespa command-line tool, see the [README](go/README.md).


## Vespa FE (fixme: better name and description here)
This is a work-in-progress javascript app for various use cases.


## vespa_query_dsl
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
