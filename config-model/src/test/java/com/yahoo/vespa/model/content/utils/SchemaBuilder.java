// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.utils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.yahoo.config.model.test.TestUtil.joinLines;

/**
 * Class for building a search definition (used for testing only).
 *
 * @author geirst
 */
public class SchemaBuilder {

    private String name = "test";
    private String content = "";

    public SchemaBuilder() {
    }

    public SchemaBuilder name(String name) {
        this.name = name;
        return this;
    }

    public SchemaBuilder content(String content) {
        this.content = content;
        return this;
    }

    public String build() {
        return joinLines("search " + name + " {",
                "  document " + name + " {",
                content,
                "  }",
                "}");
    }

    public static List<String> createSchemas(String ... docTypes) {
        return Arrays.asList(docTypes)
                .stream()
                .map(type -> new SchemaBuilder().name(type).build())
                .toList();
    }

}
