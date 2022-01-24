// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.searchdefinition.Schema;

import java.util.Collection;

/**
 * @author Tony Vaagenes
 */
// TODO: This class is quite pointless
public class NamedSchema {

    private final Schema schema;
    private final String name;

    public Schema getSearch() {
        return schema;
    }

    public String getName() {
        return name;
    }

    public NamedSchema(String name, Schema schema) {
        this.name = name;
        this.schema = schema;
    }

}
