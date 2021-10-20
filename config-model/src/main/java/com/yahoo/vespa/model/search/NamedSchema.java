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

    public static final String fileNameSuffix = ".sd";

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

    //Find search definition from a collection with the name specified
    public static NamedSchema findByName(String schemaName, Collection<NamedSchema> schemas) {
        for (NamedSchema candidate : schemas) {
            if (candidate.getName().equals(schemaName) )
                return candidate;
        }

        return null;
    }

    // Used by admin interface
    public String getFilename() {
        return getName() + fileNameSuffix;
    }

}
