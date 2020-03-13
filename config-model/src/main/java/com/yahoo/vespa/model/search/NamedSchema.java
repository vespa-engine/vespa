// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.searchdefinition.Search;

import java.util.Collection;

/**
 * @author Tony Vaagenes
 */
// TODO: This class is quite pointless
public class NamedSchema {

    private final Search search;
    private final String name;

    public static final String fileNameSuffix = ".sd";

    public Search getSearch() {
        return search;
    }

    public String getName() {
        return name;
    }

    public NamedSchema(String name, Search search) {
        this.name = name;
        this.search = search;
    }

    //Find search definition from a collection with the name specified
    public static NamedSchema findByName(final String searchDefinitionName, Collection<NamedSchema> searchDefinitions) {
        for (NamedSchema candidate : searchDefinitions) {
            if (candidate.getName().equals(searchDefinitionName) )
                return candidate;
        }

        return null;
    }

    // Used by admin interface
    public String getFilename() {
        return getName() + fileNameSuffix;
    }

}
