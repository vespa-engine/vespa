// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.properties;

import com.yahoo.processing.request.CompoundName;

import java.util.Map;

/**
 * Property aliases which contains some hardcoded unaliasing of prefixes of
 * rankfeature and rankproperty maps.
 *
 * @author bratseth
 */
public class QueryPropertyAliases extends PropertyAliases {

    /**
     * Creates an instance with a set of aliases. The given aliases will be used directly by this class.
     * To make this class immutable and thread safe, relinquish ownership of the parameter map.
     */
    public QueryPropertyAliases(Map<String,CompoundName> aliases) {
        super(aliases);
    }

    @Override
    protected CompoundName unalias(CompoundName nameOrAlias) {
        if (nameOrAlias.first().equalsIgnoreCase("rankfeature"))
            return nameOrAlias.rest().prepend("ranking", "features");
        else if (nameOrAlias.first().equalsIgnoreCase("rankproperty"))
            return nameOrAlias.rest().prepend("ranking", "properties");
        return super.unalias(nameOrAlias);
    }

}
