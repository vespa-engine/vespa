// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.provider;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.Version;
import com.yahoo.component.VersionSpecification;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A generic superclass for component registries. Supports registration and lookup
 * of components by id. The registry resolves id requests to the newest matching
 * component version registered.
 * <p>
 * This registry supports the <i>freeze</i> pattern - changes can be made
 * to this registry until {@link #freeze} is called. Subsequent change attempts will cause an
 * exception. Freezing a registry after building makes it possible to avoid locking and memory
 * synchronization on lookups.
 *
 * @author bratseth
 */
public class ComponentRegistry<COMPONENT> {

    /** All versions of all components, indexed by name and namespace */
    private final Map<ComponentId, Map<String, Map<Version, COMPONENT>>> componentsByNameByNamespace = new LinkedHashMap<>();

    /** All versions of all components indexed by id */
    private final Map<ComponentId, COMPONENT> componentsById =new LinkedHashMap<>();

    /** True when this cannot be changed any more */
    private boolean frozen = false;

    /**
     * Freezes this registry to prevent further changes. Override this to freeze internal data
     * structures and dependent objects. Overrides must call super.
     * Calling freeze on an already frozen registry must have no effect.
     */
    public synchronized void freeze() { frozen=true; }

    /** returns whether this is currently frozen */
    public final boolean isFrozen() { return frozen; }

    /**
     * Registers a component unless this registry is frozen.
     * This will succeed even if this component name and version is already registered.
     *
     * @throws IllegalStateException if this chain is frozen
     */
    public void register(ComponentId id, COMPONENT component) {
        if (frozen) throw new IllegalStateException("Cannot modify a frozen component registry");

        Map<String, Map<Version, COMPONENT>> componentVersionsByName =
                componentsByNameByNamespace.get(id.getNamespace());
        if (componentVersionsByName == null) {
            componentVersionsByName = new LinkedHashMap<>();
            componentsByNameByNamespace.put(id.getNamespace(), componentVersionsByName);
        }

        Map<Version, COMPONENT> componentVersions = componentVersionsByName.get(id.getName());
        if (componentVersions == null) {
            componentVersions = new LinkedHashMap<>();
            componentVersionsByName.put(id.getName(), componentVersions);
        }
        componentVersions.put(id.getVersion(), component);

        componentsById.put(id, component);
    }


    /**
     * Unregisters a component unless this registry is frozen.
     * Note that the component is not deconstructed or otherwise modified in any way, this
     * is the responsiblity of the caller.
     *
     * @param id the id of the component to be unregistered
     * @return the component that was unregistered, or null if no such component was already registered
     */
    public COMPONENT unregister(ComponentId id) {
        if (frozen) throw new IllegalStateException("Cannot modify a frozen component registry");

        COMPONENT removed = componentsById.remove(id);

        if (removed != null) {
            //removed is non-null, so it must be present here as well:
            Map<String, Map<Version, COMPONENT>> componentVersionsByName = componentsByNameByNamespace.get(id.getNamespace());
            Map<Version, COMPONENT> componentVersions = componentVersionsByName.get(id.getName());
            COMPONENT removedInner = componentVersions.remove(id.getVersion());
            assert (removedInner == removed);

            //clean up
            if (componentVersions.isEmpty()) {
                componentVersionsByName.remove(id.getName());
            }
            if (componentVersionsByName.isEmpty()) {
                componentsByNameByNamespace.remove(id.getNamespace());
            }
        }
        return removed;
    }

    /**
     * See getComponent(ComponentSpecification)
     * @param  componentSpecification a component specification string, see {@link com.yahoo.component.Version}
     * @return the component or null if no component of this name (and version, if specified) is registered here
     */
    public COMPONENT getComponent(String componentSpecification) {
        return getComponent(new ComponentSpecification(componentSpecification));
    }

    public COMPONENT getComponent(ComponentId id) {
        return componentsById.get(id);
    }


    /**
     * Returns a component. If the id does not specify an (exact) version, the newest (matching) version is returned.
     * For example, if version 3.1 is specified and we have 3.1.0, 3.1.1 and 3.1.3 registered, 3.1.3 is returned.
     *
     * @param id the id of the component to return. May not include a version, or include
     *        an underspecified version, in which case the highest (matching) version which
     *        does not contain a qualifier is returned
     * @return the search chain or null if no component of this name (and matching version, if specified) is registered
     */
    public COMPONENT getComponent(ComponentSpecification id) {
        Map<String, Map<Version, COMPONENT>> componentVersionsByName = componentsByNameByNamespace.get(id.getNamespace());
        if (componentVersionsByName == null) return null;  // No matching namespace

        Map<Version, COMPONENT> versions = componentVersionsByName.get(id.getName());
        if (versions==null) return null; // No versions of this component

        Version version=findBestMatch(id.getVersionSpecification(), versions.keySet());
        //if (version==null) return null; // No matching version

        return versions.get(version);
    }

    /**
     * Finds the best (highest) matching version among a set.
     *
     * @return the matching version, or null if there are no matches
     */
    protected static Version findBestMatch(VersionSpecification versionSpec, Set<Version> versions) {
        Version bestMatch=null;
        for (Version version : versions) {
            //No version is set if getSpecifiedMajor() == null
            //In that case we allow all versions
            if (version == null || !versionSpec.matches(version)) continue;

            if (bestMatch==null || bestMatch.compareTo(version)<0)
                bestMatch=version;
        }
        return bestMatch;
    }

    /**
     * Returns an unmodifiable snapshot of all components present in this registry.
     */
    public List<COMPONENT> allComponents() {
        return ImmutableList.copyOf(componentsById.values());
    }

    /**
     * Returns an unmodifiable snapshot of all components present in this registry, by id.
     */
    public Map<ComponentId, COMPONENT> allComponentsById() {
        return ImmutableMap.copyOf(componentsById);
    }

    /** Returns the number of components in this */
    public int getComponentCount() { return componentsById.size(); }

    /** Returns a frozen registry with a single component, for convenience */
    public static <COMPONENT> ComponentRegistry<COMPONENT> singleton(ComponentId id, COMPONENT component) {
        ComponentRegistry<COMPONENT> registry = new ComponentRegistry<>();
        registry.register(id, component);
        registry.freeze();
        return registry;
    }

}
