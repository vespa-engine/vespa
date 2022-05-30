// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profile.types.QueryProfileTypeRegistry;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A set of query profiles. This also holds the query profile types as a dependent registry
 *
 * @author bratseth
 */
public class QueryProfileRegistry extends ComponentRegistry<QueryProfile> {

    private int nextAnonymousId = 0;

    private final QueryProfileTypeRegistry queryProfileTypeRegistry = new QueryProfileTypeRegistry();

    /** Register this type by its id */
    public void register(QueryProfile profile) {
        super.register(profile.getId(), profile);
    }

    /** Returns a query profile type by name, or null if not found */
    public QueryProfileType getType(String type) {
        return queryProfileTypeRegistry.getComponent(type);
    }

    /** Returns the type registry attached to this */
    public QueryProfileTypeRegistry getTypeRegistry() { return queryProfileTypeRegistry; }

    /**
     * <p>Returns a query profile for the given request string, or null if a suitable one is not found.</p>
     *
     * The request string must be a valid {@link com.yahoo.component.ComponentId} or null.
     *
     * <p>
     * If the string is null, the profile named "default" is returned, or null if that does not exists.
     *
     * <p>
     * The version part (if any) is matched used the usual component version patching rules.
     * If the name part matches a query profile name perfectly, that profile is returned.
     * If not, and the name is a slash-separated path, the profile with the longest matching left sub-path
     * which has a type which allows path mahting is used. If there is no such profile, null is returned.
     */
    public QueryProfile findQueryProfile(String idString) {
        if (idString == null) return getComponent("default");
        ComponentSpecification id = new ComponentSpecification(idString);
        QueryProfile profile = getComponent(id);
        if (profile != null) return profile;

        return findPathParentQueryProfile(new ComponentSpecification(idString));
    }

    private QueryProfile findPathParentQueryProfile(ComponentSpecification id) {
        // Try the name with "/" appended - should have the same semantics with path matching
        QueryProfile slashedProfile = getComponent(new ComponentSpecification(id.getName() + "/", id.getVersionSpecification()));
        if (slashedProfile != null && slashedProfile.getType() != null && slashedProfile.getType().getMatchAsPath())
            return slashedProfile;

        // Extract the parent (if any)
        int slashIndex = id.getName().lastIndexOf("/");
        if (slashIndex < 1) return null;
        String parentName = id.getName().substring(0,slashIndex);

        ComponentSpecification parentId = new ComponentSpecification(parentName, id.getVersionSpecification());

        QueryProfile pathParentProfile = getComponent(parentId);

        if (pathParentProfile != null && pathParentProfile.getType() != null && pathParentProfile.getType().getMatchAsPath())
            return pathParentProfile;
        return findPathParentQueryProfile(parentId);
    }

    /** Freezes this, and all owned query profiles and query profile types */
    @Override
    public void freeze() {
        if (isFrozen()) return;
        queryProfileTypeRegistry.freeze();
        for (QueryProfile queryProfile : allComponents())
            queryProfile.freeze();
    }

    public CompiledQueryProfileRegistry compile() { return QueryProfileCompiler.compile(this); }

    public ComponentId createAnonymousId(String name) {
        return ComponentId.newAnonymous(name + "_" + (nextAnonymousId++));
    }

}
