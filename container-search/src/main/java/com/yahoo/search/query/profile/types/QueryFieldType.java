// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types;

import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.yql.YqlQuery;

/**
 * A YQL query template field type in a query profile
 *
 * @author bratseth
 */
public class QueryFieldType extends FieldType {

    @Override
    public Class getValueClass() { return YqlQuery.class; }

    @Override
    public String stringValue() { return "query"; }

    @Override
    public String toString() { return "field type " + stringValue(); }

    @Override
    public String toInstanceDescription() { return "a YQL query template"; }

    @Override
    public Object convertFrom(Object o, QueryProfileRegistry registry) {
        if (o instanceof YqlQuery) return o;
        if (o instanceof String) return YqlQuery.from((String)o);
        return null;
    }

    @Override
    public Object convertFrom(Object o, ConversionContext context) {
        return convertFrom(o, (QueryProfileRegistry)null);
    }

}
