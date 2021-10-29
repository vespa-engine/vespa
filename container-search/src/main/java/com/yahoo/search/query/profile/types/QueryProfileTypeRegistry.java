// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types;

import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.QueryProfileRegistry;

/**
 * A registry of query profile types
 *
 * @author bratseth
 */
public class QueryProfileTypeRegistry extends ComponentRegistry<QueryProfileType> {

    private int nextAnonymousId = 0;

    private final int nativeProfileCount;

    public QueryProfileTypeRegistry() {
        Query.addNativeQueryProfileTypesTo(this);
        nativeProfileCount = allComponents().size();
    }

    /** Register this type by its id */
    public void register(QueryProfileType type) {
        super.register(type.getId(), type);
    }

    /** Returns true if this has types in addition to the native Vespa types */
    public boolean hasApplicationTypes() {
        return allComponents().size() > nativeProfileCount;
    }

    @Override
    public void freeze() {
        if (isFrozen()) return;
        for (QueryProfileType queryProfileType : allComponents())
            queryProfileType.freeze();
    }

    public static QueryProfileTypeRegistry emptyFrozen() {
        QueryProfileTypeRegistry registry = new QueryProfileTypeRegistry();
        registry.freeze();
        return registry;
    }

    public ComponentId createAnonymousId(String name) {
        return ComponentId.newAnonymous(name + "_" + (nextAnonymousId++));
    }

}
