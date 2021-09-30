// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.yahoo.processing.request.CompoundName;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * A query profile visitor which keeps track of name prefixes and can skip values outside a given prefix
 *
 * @author bratseth
 */
abstract class PrefixQueryProfileVisitor extends QueryProfileVisitor {

    protected final CompoundNameChildCache cache;

    /** Only call onValue/onQueryProfile for nodes having this prefix */
    private final CompoundName prefix;

    /** The current prefix, relative to prefix. */
    protected CompoundName currentPrefix = CompoundName.empty;
    private final Deque<CompoundName> currentPrefixes = new ArrayDeque<>();

    private int prefixComponentIndex = -1;

    public PrefixQueryProfileVisitor(CompoundName prefix, CompoundNameChildCache cache) {
        if (prefix == null)
            prefix = CompoundName.empty;
        this.prefix = prefix;
        this.cache = cache;
    }

    @Override
    public final void onQueryProfile(QueryProfile profile,
                                     DimensionBinding binding,
                                     QueryProfile owner,
                                     DimensionValues variant) {
        if (prefixComponentIndex < prefix.size()) return; // Not in the prefix yet
        onQueryProfileInsidePrefix(profile, binding, owner, variant);
    }

    protected abstract void onQueryProfileInsidePrefix(QueryProfile profile,
                                                       DimensionBinding binding,
                                                       QueryProfile owner,
                                                       DimensionValues variant);

    @Override
    public final boolean enter(String name) {
        if (prefixComponentIndex++ < prefix.size()) return true; // we're in the given prefix, which should not be included in the name
        if ( ! name.isEmpty()) {
            currentPrefixes.push(currentPrefix);
            currentPrefix = cache.append(currentPrefix, name);
        }
        return true;
    }

    @Override
    public final void leave(String name) {
        if (--prefixComponentIndex < prefix.size()) return; // we're in the given prefix, which should not be included in the name
        if ( ! name.isEmpty())
            currentPrefix = currentPrefixes.pop();
    }

    /**
     * Returns the correct prefix component if we are still going down the prefix path,
     * or null to get all if we are inside the prefix
     */
    @Override
    public String getLocalKey() {
        if (prefixComponentIndex < prefix.size())
            return prefix.get(prefixComponentIndex);
        else
            return null;
    }

}
