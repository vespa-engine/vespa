// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

/**
 * Instances of this is used to visit nodes in a graph of query profiles
 *
 * <code>
 * Visitor are called in the following sequence on each query profile:
 * enter=enter(referenceName);
 * onQueryProfile(this)
 * if (enter) {
 *   getLocalKey()
 *     ...calls on nested content found in variants, this and inherited, in that order
 *   leave(referenceName)
 * }
 *
 * The first enter call will be on the root node, which has an empt reference name.
 * </code>
 *
 * @author bratseth
 */
abstract class QueryProfileVisitor {

    /**
     * Called when a new <b>nested</b> profile in the graph is entered.
     * This default implementation does nothing but returning true.
     * If the node is entered (if true is returned from this), a corresponding {@link #leave(String)} call will happen
     * later.
     *
     * @param name the name this profile is nested as, or the empty string if we are entering the root profile
     * @return whether we should visit the content of this node or not
     */
    public boolean enter(String name) { return true; }

    /**
     * Called when the last {@link #enter(String) entered} nested profile is left.
     * That is: One leave call is made for each enter call which returns true,
     * but due to nesting those calls are not necessarily alternating.
     * This default implementation does nothing.
     */
    public void leave(String name) { }

    /**
     * Called when a value (not a query profile) is encountered.
     *
     * @param localName the local name of this value (the full name, if needed, must be reconstructed
     *        by the information given by the history of {@link #enter(String)} and {@link #leave(String)} calls
     * @param value the value
     * @param binding the binding this holds for
     * @param owner the query profile having this value, or null only when profile is the root profile
     * @param variant the variant having this value, or null if it is not in a variant
     */
    public abstract void onValue(String localName,
                                 Object value,
                                 DimensionBinding binding,
                                 QueryProfile owner,
                                 DimensionValues variant);

    /**
     * Called when a query profile is encountered.
     *
     * @param profile the query profile reference encountered
     * @param binding the binding this holds for
     * @param owner the profile making this reference, or null only when profile is the root profile
     * @param variant the variant having this value, or null if it is not in a variant
     */
    public abstract void onQueryProfile(QueryProfile profile,
                                        DimensionBinding binding,
                                        QueryProfile owner,
                                        DimensionValues variant);

    /** Returns whether this visitor is done visiting what it needed to visit at this point */
    public abstract boolean isDone();

    /** Returns whether we should, at this point, visit inherited profiles. This default implementation returns true */
    public boolean visitInherited() { return true; }

    /**
     * Returns the current local key which should be visited in the last {@link #enter(String) entered} sub-profile
     * (or in the top level profile if none is entered), or null to visit all content
     */
    public abstract String getLocalKey();

    /**
     * Calls onValue or onQueryProfile on this and visits the content if it's a profile
     *
     * @param variant the variant having this value, or null if it is not in a variant
     */
    final void acceptValue(String key,
                           Object value,
                           DimensionBinding dimensionBinding,
                           QueryProfile owner,
                           DimensionValues variant) {
        if (value == null) return;
        if (value instanceof QueryProfile) {
            QueryProfile queryProfileValue = (QueryProfile)value;
            queryProfileValue.acceptAndEnter(key, this, dimensionBinding.createFor(queryProfileValue.getDimensions()), owner);
        }
        else {
            onValue(key, value, dimensionBinding, owner, variant);
        }
    }

}
