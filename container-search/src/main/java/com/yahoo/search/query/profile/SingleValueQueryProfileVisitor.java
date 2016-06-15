// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import java.util.List;

/**
 * Visitor which stores the first non-query-profile value encountered,
 * or the first query profile encountered at a stop where we do not have any name components left which can be used to
 * visit further subprofiles. Hence this may be used both to get the highest prioritized primitive
 * value, or query profile, whichever is encountered first which matches the name.
 * <p>
 *
 * @author <a href="mailto:bratseth@yahoo-inc.com">Jon Bratseth</a>
 */
final class SingleValueQueryProfileVisitor extends QueryProfileVisitor {

    /** The value found, or null if none */
    private Object value=null;

    private final List<String> name;

    private int nameIndex=-1;

    private final boolean allowQueryProfileResult;

    private boolean enteringContent=true;

    public SingleValueQueryProfileVisitor(List<String> name,boolean allowQueryProfileResult) {
        this.name=name;
        this.allowQueryProfileResult=allowQueryProfileResult;
    }

    public @Override String getLocalKey() {
        return name.get(nameIndex);
    }

    public @Override boolean enter(String name) {
        if (nameIndex+1<this.name.size()) {
            nameIndex++;
            enteringContent=true;
        }
        else {
            enteringContent=false;
        }
        return enteringContent;
    }

    public @Override void leave(String name) {
        nameIndex--;
    }

    public @Override void onValue(String key,Object value, DimensionBinding binding, QueryProfile owner) {
        if (nameIndex==name.size()-1)
            this.value=value;
    }

    public @Override void onQueryProfile(QueryProfile profile,DimensionBinding binding, QueryProfile owner) {
        if (enteringContent) return; // still waiting for content
        if (allowQueryProfileResult)
            this.value = profile;
        else
            this.value = profile.getValue();
    }

    public @Override boolean isDone() {
        return value!=null;
    }

    /** Returns the value found during visiting, or null if none */
    public Object getResult() { return value; }

    public @Override String toString() {
        return "a single value visitor (hash " + hashCode() + ") with current value " + value;
    }

}
