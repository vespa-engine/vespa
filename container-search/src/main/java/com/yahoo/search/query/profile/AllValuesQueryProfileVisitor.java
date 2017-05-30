// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.yahoo.processing.request.CompoundName;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author bratseth
 */
final class AllValuesQueryProfileVisitor extends PrefixQueryProfileVisitor {

    private Map<String,Object> values=new HashMap<>();

    /* Lists all values starting at prefix */
    public AllValuesQueryProfileVisitor(CompoundName prefix) {
        super(prefix);
    }

    @Override
    public void onValue(String localName, Object value, DimensionBinding binding, QueryProfile owner) {
        putValue(localName, value, values);
    }

    @Override
    public void onQueryProfileInsidePrefix(QueryProfile profile, DimensionBinding binding, QueryProfile owner) {
        putValue("", profile.getValue(), values);
    }

    private final void putValue(String key, Object value, Map<String, Object> values) {
        if (value == null) return;
        CompoundName fullName = currentPrefix.append(key);
        if (fullName.isEmpty()) return; // Avoid putting a non-leaf (subtree) root in the list
        if (values.containsKey(fullName.toString())) return; // The first value encountered has priority
        values.put(fullName.toString(), value);
    }

    /** Returns the values resulting from this visiting */
    public Map<String, Object> getResult() { return values; }

    /** Returns false - we are not done until we have seen all */
    public boolean isDone() { return false; }

}
