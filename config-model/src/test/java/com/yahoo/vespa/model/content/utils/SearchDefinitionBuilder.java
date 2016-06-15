// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.utils;

/**
 * Class for building a search definition (used for testing only).
 *
 * @author <a href="mailto:geirst@yahoo-inc.com">Geir Storli</a>
 */
public class SearchDefinitionBuilder {

    private String name = "test";
    private String content = "";

    public SearchDefinitionBuilder() {
    }

    public SearchDefinitionBuilder name(String name) {
        this.name = name;
        return this;
    }

    public SearchDefinitionBuilder content(String content) {
        this.content = content;
        return this;
    }

    public String build() {
        return "search " + name + " {\n" +
                "  document " + name + " {\n" +
                content + "\n" +
                "  }\n" +
                "}";
    }

}
