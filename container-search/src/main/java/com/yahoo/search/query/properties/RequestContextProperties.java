// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.properties;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.Properties;

import java.util.Collections;
import java.util.Map;

/**
 * Turns get(name) into get(name, context) using the request given at construction time
 * and the zone info.
 * This is used to allow the query's request to be supplied to all property requests
 * without forcing users of the query.properties() to supply this explicitly.
 *
 * @author bratseth
 */
public class RequestContextProperties extends Properties {

    private final Map<String, String> context;

    public RequestContextProperties(Map<String, String> properties) {
        this.context = Collections.unmodifiableMap(properties);
    }

    @Override
    public Object get(CompoundName name, Map<String,String> context,
                      com.yahoo.processing.request.Properties substitution) {
        return super.get(name, context == null ? this.context : context, substitution);
    }

    @Override
    public void set(CompoundName name, Object value, Map<String,String> context) {
        super.set(name, value, context == null ? this.context : context);
    }

    @Override
    public Map<String, Object> listProperties(CompoundName path, Map<String,String> context,
                                              com.yahoo.processing.request.Properties substitution) {
        return super.listProperties(path, context == null ? this.context : context, substitution);
    }

}
