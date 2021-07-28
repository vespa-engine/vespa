// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    private String documentFields = "";
    private String schemaFields = "";

    public SchemaBuilder() {
    }

    public SchemaBuilder name(String name) {
        this.name = name;
        return this;
    }

    public SchemaBuilder documentFields(String content) {
        this.documentFields = content;
        return this;
    }

    public SchemaBuilder schemaFields(String extra) {
        this.schemaFields = extra;
        return this;
    }
    public String build() {
        return joinLines("schema " + name + " {",
                "  document " + name + " {",
                documentFields,
                "  }",
                schemaFields,
                "}");
    }

    public static List<String> createSchemas(String ... docTypes) {
        return Arrays.asList(docTypes)
                .stream()
                .map(type -> new SchemaBuilder().name(type).build())
                .collect(Collectors.toList());
    }

}
