<!-- Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->

A standalone Java library for composing [YQL queries](https://docs.vespa.ai/en/reference/querying/yql.html).
For usage, refer to the [QTest.java](test/java/ai/vespa/client/dsl/QTest.java) unit test.

To include this dependency using Maven:

    <dependency>
      <groupId>com.yahoo.vespa</groupId>
      <artifactId>client</artifactId>
      <version>[Vespa version]</version>
    </dependency>


Known gaps:

- Support for `predicate` (https://docs.vespa.ai/en/querying/predicate-fields.html) is missing.
- Methods for checking positive/negative conditions for a specific field is missing.
- Support for the `labeled` item.
- Support for the `text` item.
- Support for searching in maps.
