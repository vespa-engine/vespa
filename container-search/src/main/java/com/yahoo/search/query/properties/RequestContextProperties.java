// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.properties;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.Properties;

import java.util.Map;

/**
 * Turns get(name) into get(name,request) using the request given at construction time.
 * This is used to allow the query's request to be supplied to all property requests
 * without forcing users of the query.properties() to supply this explicitly.
 *
 * @author bratseth
 */
public class RequestContextProperties extends Properties {

    private final Map<String, String> requestMap;

    public RequestContextProperties(Map<String, String> properties) {
        this.requestMap = properties;
    }

    @Override
    public Object get(CompoundName name,Map<String,String> context,
                      com.yahoo.processing.request.Properties substitution) {
        return super.get(name, context == null ? requestMap : context, substitution);
    }

    @Override
    public void set(CompoundName name,Object value,Map<String,String> context) {
        super.set(name, value, context == null ? requestMap : context);
    }

    @Override
    public Map<String, Object> listProperties(CompoundName path,Map<String,String> context,
                                              com.yahoo.processing.request.Properties substitution) {
        return super.listProperties(path, context == null ? requestMap : context, substitution);
    }

}
