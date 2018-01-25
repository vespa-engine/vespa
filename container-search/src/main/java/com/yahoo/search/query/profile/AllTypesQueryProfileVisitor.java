// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileFieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.HashMap;
import java.util.Map;

/**
 * @author bratseth
 */
final class AllTypesQueryProfileVisitor extends PrefixQueryProfileVisitor {

    /** A map of query profile types */
    private Map<CompoundName, QueryProfileType> types = new HashMap<>();

    public AllTypesQueryProfileVisitor(CompoundName prefix) {
        super(prefix);
    }

    @Override
    public void onValue(String name, Object value, DimensionBinding binding, QueryProfile owner) {}


    @Override
    public void onQueryProfileInsidePrefix(QueryProfile profile, DimensionBinding binding, QueryProfile owner) {
        if (profile.getType() != null)
            addReachableTypes(currentPrefix, profile.getType());
    }

    private void addReachableTypes(CompoundName name, QueryProfileType type) {
        types.putIfAbsent(name, type); // Types visited earlier has precedence: profile.type overrides profile.inherited.type
        for (FieldDescription fieldDescription : type.fields().values()) {
            if ( ! (fieldDescription.getType() instanceof QueryProfileFieldType)) continue;
            QueryProfileFieldType fieldType = (QueryProfileFieldType)fieldDescription.getType();
            if (fieldType.getQueryProfileType() !=null) {
                addReachableTypes(name.append(fieldDescription.getName()), fieldType.getQueryProfileType());
            }
        }
    }

    /** Returns the values resulting from this visiting */
    public Map<CompoundName, QueryProfileType> getResult() { return types; }

    /** Returns false - we are not done until we have seen all */
    public boolean isDone() { return false; }

}
