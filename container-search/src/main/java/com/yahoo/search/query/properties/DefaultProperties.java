// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.properties;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.Properties;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.List;
import java.util.Map;

/**
 * Default values for properties that are meant to be customized in query profiles.
 * 
 * @author Tony Vaagenes
 */
public final class DefaultProperties extends Properties  {

    public static final CompoundName MAX_OFFSET = CompoundName.from("maxOffset");
    public static final CompoundName MAX_HITS = CompoundName.from("maxHits");
    public static final CompoundName MAX_QUERY_ITEMS = CompoundName.from("maxQueryItems");

    public static final QueryProfileType argumentType = new QueryProfileType("DefaultProperties");

    private static final List<CompoundName> properties = List.of(MAX_OFFSET, MAX_HITS, MAX_QUERY_ITEMS);

    static {
        argumentType.setBuiltin(true);
        properties.forEach(property -> argumentType.addField(new FieldDescription(property.toString(), "integer")));
        argumentType.freeze();
    }

    @Override
    public Object get(CompoundName name, Map<String, String> context, com.yahoo.processing.request.Properties substitution) {
        if (name.equals(MAX_OFFSET)) return 1000;
        if (name.equals(MAX_HITS)) return 400;
        if (name.equals(MAX_QUERY_ITEMS)) return 10000;
        return super.get(name, context, substitution);
    }

    public static void requireNotPresentIn(Map<String, String> map) {
        for (var property : properties) {
            if (map.containsKey(property.toString()))
                throw new IllegalArgumentException(property + " must be specified in a query profile.");
        }
    }

}
