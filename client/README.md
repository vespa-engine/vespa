# vespa_query_dsl
This lib is used for composing vespa YQL queries

Reference: https://docs.vespa.ai/en/reference/query-language-reference.html

# usage
Please refer to the unit test:

https://github.com/vespa-engine/vespa/tree/master/client/src/test/groovy/ai/vespa/client/dsl/QTest.groovy

# todos
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
