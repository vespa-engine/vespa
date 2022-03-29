// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;

import java.util.Map;

/**
 * Object properties keyed by name which can be looked up using default values and
 * with conversion to various primitive wrapper types.
 * <p>
 * Multiple property implementations can be chained to provide unified access to properties
 * backed by multiple sources as a Chain of Responsibility.
 * <p>
 * For better performance, prefer CompoundName argument constants over Strings.
 * <p>
 * Properties can be cloned. Cloning a properties instance returns a new instance
 * which chains new instances of all chained instances. The content within each instance
 * is cloned to the extent determined appropriate by that implementation.
 * <p>
 * This base class simply passes all access on to the next in chain.
 *
 * @author bratseth
 */
public abstract class Properties extends com.yahoo.processing.request.Properties {

    @Override
    public Properties chained() { return (Properties)super.chained(); }

    @Override
    public Properties clone() {
        return (Properties)super.clone();
    }

    /**
     * Returns the query owning this property object.
     * Only guaranteed to work if this instance is accessible as query.properties()
     */
    public Query getParentQuery() {
        if (chained() == null) {
            throw new RuntimeException("getParentQuery should only be called on a properties instance accessible as query.properties()");
        } else {
            return chained().getParentQuery();
        }
    }

    /**
     * Invoked during deep cloning of the parent query.
     */
    public void setParentQuery(Query query) {
        if (chained() != null)
            chained().setParentQuery(query);
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
