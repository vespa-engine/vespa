// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.yahoo.search.query.profile.types.FieldDescription;

import java.util.List;

/**
 * @author bratseth
 */
final class FieldDescriptionQueryProfileVisitor extends QueryProfileVisitor {

    /** The result, or null if none */
    private FieldDescription result = null;

    private final List<String> name;

    private int nameIndex=-1;

    private boolean enteringContent=false;

    public FieldDescriptionQueryProfileVisitor(List<String> name) {
        this.name=name;
    }

    @Override
    public String getLocalKey() {
        return name.get(nameIndex);
    }

    @Override
    public boolean enter(String name) {
        if (nameIndex+2<this.name.size()) {
            nameIndex++;
            enteringContent=true;
        }
        else {
            enteringContent=false;
        }
        return enteringContent;
    }

    @Override
    public void leave(String name) {
        nameIndex--;
    }

    @Override
    public void onValue(String name,
                        Object value,
                        DimensionBinding binding,
                        QueryProfile owner,
                        DimensionValues variant) {
    }

    @Override
    public void onQueryProfile(QueryProfile profile,
                               DimensionBinding binding,
                               QueryProfile owner,
                               DimensionValues variant) {
        if (enteringContent) return; // not at leaf query profile
        if (profile.getType() == null) return;
        result = profile.getType().getField(name.get(name.size() - 1));
    }

    @Override
    public boolean isDone() {
        return result != null;
    }

    public FieldDescription result() { return result; }

    @Override
    public String toString() {
        return "a query profile type visitor (hash " + hashCode() + ") with current value " + result;
    }
}
