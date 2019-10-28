// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.yahoo.processing.request.CompoundName;

import java.util.HashSet;
import java.util.Set;

/**
 * @author bratseth
 */
final class AllUnoverridableQueryProfileVisitor extends PrefixQueryProfileVisitor {

    /** A map of query profile types */
    private Set<CompoundName> unoverridables = new HashSet<>();

    public AllUnoverridableQueryProfileVisitor(CompoundName prefix) {
        super(prefix);
    }

    @Override
    public void onValue(String name, Object value,
                        DimensionBinding binding,
                        QueryProfile owner,
                        DimensionValues variant) {
        addUnoverridable(name, currentPrefix.append(name), binding, owner);
    }

    @Override
    public void onQueryProfileInsidePrefix(QueryProfile profile,
                                           DimensionBinding binding,
                                           QueryProfile owner,
                                           DimensionValues variant) {
        addUnoverridable(currentPrefix.last(), currentPrefix, binding, owner);
    }

    private void addUnoverridable(String localName,
                                  CompoundName fullName,
                                  DimensionBinding binding,
                                  QueryProfile owner) {
        if (owner == null) return;

        Boolean isOverridable = owner.isLocalOverridable(localName, binding);
        if (isOverridable != null &&  ! isOverridable)
            unoverridables.add(fullName);
    }

    /** Returns the values resulting from this visiting */
    public Set<CompoundName> getResult() { return unoverridables; }

    /** Returns false - we are not done until we have seen all */
    public boolean isDone() { return false; }

}
