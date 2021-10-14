// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.compiled;

import com.google.inject.Inject;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileCompiler;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.config.QueryProfileConfigurer;
import com.yahoo.search.query.profile.config.QueryProfilesConfig;
import com.yahoo.search.query.profile.types.QueryProfileTypeRegistry;

/**
 * A set of compiled query profiles.
 *
 * @author bratseth
 */
public class CompiledQueryProfileRegistry extends ComponentRegistry<CompiledQueryProfile> {

    /** The empty, frozen registry */
    public static final CompiledQueryProfileRegistry empty = CompiledQueryProfileRegistry.createFrozen();
    
    private final QueryProfileTypeRegistry typeRegistry;

    @Inject
    public CompiledQueryProfileRegistry(QueryProfilesConfig config) {
        QueryProfileRegistry registry = QueryProfileConfigurer.createFromConfig(config);
        typeRegistry = registry.getTypeRegistry();
        for (QueryProfile inputProfile : registry.allComponents()) {
            register(QueryProfileCompiler.compile(inputProfile, this));
        }
    }

    /** Creates a compiled query profile registry with no types */
    public CompiledQueryProfileRegistry() {
        this(QueryProfileTypeRegistry.emptyFrozen());
    }

    public CompiledQueryProfileRegistry(QueryProfileTypeRegistry typeRegistry) {
        this.typeRegistry = typeRegistry;
    }

    /** Registers a type by its id */
    public final void register(CompiledQueryProfile profile) {
        super.register(profile.getId(), profile);
    }

    public QueryProfileTypeRegistry getTypeRegistry() { return typeRegistry; }

    /**
     * <p>Returns a query profile for the given request string, or null if a suitable one is not found.</p>
     *
     * The request string must be a valid {@link com.yahoo.component.ComponentId} or null.<br>
     * If the string is null, the profile named "default" is returned, or null if that does not exists.
     *
     * <p>
     * The version part (if any) is matched used the usual component version patching rules.
     * If the name part matches a query profile name perfectly, that profile is returned.
     * If not, and the name is a slash-separated path, the profile with the longest matching left sub-path
     * which has a type which allows path matching is used. If there is no such profile, null is returned.
     */
    public CompiledQueryProfile findQueryProfile(String idString) {
        if (idString == null || idString.isEmpty()) return getComponent("default");
        ComponentSpecification id = new ComponentSpecification(idString);
        CompiledQueryProfile profile = getComponent(id);
        if (profile != null) return profile;

        return findPathParentQueryProfile(new ComponentSpecification(idString));
    }

    private CompiledQueryProfile findPathParentQueryProfile(ComponentSpecification id) {
        // Try the name with "/" appended - should have the same semantics with path matching
        CompiledQueryProfile slashedProfile = getComponent(new ComponentSpecification(id.getName() + "/", 
                                                                                      id.getVersionSpecification()));
        if (slashedProfile != null && slashedProfile.getType() != null && slashedProfile.getType().getMatchAsPath())
            return slashedProfile;

        // Extract the parent (if any)
        int slashIndex = id.getName().lastIndexOf("/");
        if (slashIndex < 1) return null;
        String parentName = id.getName().substring(0, slashIndex);
        if (parentName.equals("")) return null;

        ComponentSpecification parentId = new ComponentSpecification(parentName,id.getVersionSpecification());

        CompiledQueryProfile pathParentProfile = getComponent(parentId);

        if (pathParentProfile!=null && pathParentProfile.getType() != null && pathParentProfile.getType().getMatchAsPath())
            return pathParentProfile;
        return findPathParentQueryProfile(parentId);
    }
    
    private static CompiledQueryProfileRegistry createFrozen() {
        CompiledQueryProfileRegistry registry = new CompiledQueryProfileRegistry();
        registry.freeze();
        return registry;
    }

}
