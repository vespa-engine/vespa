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

    private final Map<String, ValueWithSource> values = new HashMap<>();

    /* Lists all values starting at prefix */
    public AllValuesQueryProfileVisitor(CompoundName prefix, CompoundNameChildCache pathCache) {
        super(prefix, pathCache);
    }

    @Override
    public void onValue(String localName,
                        Object value,
                        DimensionBinding binding,
                        QueryProfile owner,
                        DimensionValues variant) {
        putValue(localName, value, null, owner, variant, binding);
    }

    @Override
    public void onQueryProfileInsidePrefix(QueryProfile profile,
                                           DimensionBinding binding,
                                           QueryProfile owner,
                                           DimensionValues variant) {
        putValue("", profile.getValue(), profile, owner, variant, binding);
    }

    private void putValue(String key,
                          Object value,
                          QueryProfile profile,
                          QueryProfile owner,
                          DimensionValues variant,
                          DimensionBinding binding) {
        CompoundName fullName = cache.append(currentPrefix, key);

        ValueWithSource existing = values.get(fullName.toString());

        // The first value encountered has priority and values have priority over profiles
        if (existing != null && (existing.value() != null || value == null)) return;

        Boolean isOverridable = owner != null ? owner.isLocalOverridable(key, binding) : null;

        values.put(fullName.toString(), new ValueWithSource(value,
                                                            owner == null ? "anonymous" : owner.getSource(),
                                                            isOverridable != null &&  ! isOverridable,
                                                            profile != null,
                                                            profile == null ? null : profile.getType(),
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
