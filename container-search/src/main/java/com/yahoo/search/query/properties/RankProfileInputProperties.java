// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.properties;

import com.yahoo.api.annotations.Beta;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.query.Properties;

import java.util.Map;

/**
 * Verifies and converts properties according to any input declarations in the rank profile set on the query.
 *
 * @author bratseth
 */
@Beta
public class RankProfileInputProperties extends Properties {

    private final Query query;

    public RankProfileInputProperties(Query query) {
        this.query = query;
    }

    /**
     * Throws IllegalInputException if the given key cannot be set to the given value.
     * This default implementation just passes to the chained properties, if any.
     */
    public void requireSettable(CompoundName name, Object value, Map<String, String> context) {
        if (chained() != null)
            chained().requireSettable(name, value, context);
    }

}
