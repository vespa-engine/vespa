// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileFieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author bratseth
 */
final class AllReferencesQueryProfileVisitor extends PrefixQueryProfileVisitor {

    /** A map of query profile types */
    private Set<CompoundName> references = new HashSet<>();

    public AllReferencesQueryProfileVisitor(CompoundName prefix) {
        super(prefix);
    }

    @Override
    public void onValue(String name, Object value,
                        DimensionBinding binding,
                        QueryProfile owner,
                        DimensionValues variant) {}

    @Override
    public void onQueryProfileInsidePrefix(QueryProfile profile,
                                           DimensionBinding binding,
                                           QueryProfile owner,
                                           DimensionValues variant) {
        references.add(currentPrefix);
    }

    /** Returns the values resulting from this visiting */
    public Set<CompoundName> getResult() { return references; }

    /** Returns false - we are not done until we have seen all */
    public boolean isDone() { return false; }

}
