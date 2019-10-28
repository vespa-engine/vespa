// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.profile.compiled.ValueWithSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author bratseth
 */
final class AllValuesQueryProfileVisitor extends PrefixQueryProfileVisitor {

    private Map<String, ValueWithSource> values = new HashMap<>();

    /* Lists all values starting at prefix */
    public AllValuesQueryProfileVisitor(CompoundName prefix) {
        super(prefix);
    }

    @Override
    public void onValue(String localName,
                        Object value,
                        DimensionBinding binding,
                        QueryProfile owner,
                        DimensionValues variant) {
        putValue(localName, value, owner, variant);
    }

    @Override
    public void onQueryProfileInsidePrefix(QueryProfile profile,
                                           DimensionBinding binding,
                                           QueryProfile owner,
                                           DimensionValues variant) {
        putValue("", profile.getValue(), owner, variant);
    }

    private void putValue(String key, Object value, QueryProfile owner, DimensionValues variant) {
        if (value == null) return;
        CompoundName fullName = currentPrefix.append(key);
        if (fullName.isEmpty()) return; // Avoid putting a non-leaf (subtree) root in the list
        if (values.containsKey(fullName.toString())) return; // The first value encountered has priority

        values.put(fullName.toString(), new ValueWithSource(value,
                                                            owner == null ? "anonymous" : owner.getSource(),
                                                            variant));
    }

    /** Returns the values resulting from this visiting */
    public Map<String, Object> values() {
        Map<String, Object> values = new HashMap<>();
        for (var entry : this.values.entrySet())
            values.put(entry.getKey(), entry.getValue().value());
        return values;
    }

    /** Returns the values with source resulting from this visiting */
    public Map<String, ValueWithSource> valuesWithSource() { return Collections.unmodifiableMap(values); }

    /** Returns false - we are not done until we have seen all */
    public boolean isDone() { return false; }

}
