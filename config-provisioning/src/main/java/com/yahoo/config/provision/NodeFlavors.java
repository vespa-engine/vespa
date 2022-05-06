// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provisioning.FlavorsConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * All the flavors configured in this zone (i.e this should be called HostFlavors).
 *
 * @author bratseth
 */
public class NodeFlavors {

    /** Flavors which are configured in this zone */
    private final Map<String, Flavor> configuredFlavors;

    @Inject
    public NodeFlavors(FlavorsConfig config) {
        this(toFlavors(config));
    }

    public NodeFlavors(Collection<Flavor> flavors) {
        configuredFlavors = flavors.stream().collect(Collectors.toUnmodifiableMap(f -> f.name(), f -> f));
    }

    public List<Flavor> getFlavors() {
        return new ArrayList<>(configuredFlavors.values());
    }

    /** Returns a flavor by name, or empty if there is no flavor with this name and it cannot be created on the fly. */
    public Optional<Flavor> getFlavor(String name) {
        if (configuredFlavors.containsKey(name))
            return Optional.of(configuredFlavors.get(name));

        NodeResources nodeResources = NodeResources.fromLegacyName(name);
        return Optional.of(new Flavor(nodeResources));
    }

    /**
     * Returns the flavor with the given name or throws an IllegalArgumentException if it does not exist
     * and cannot be created on the fly.
     */
    public Flavor getFlavorOrThrow(String flavorName) {
        return getFlavor(flavorName).orElseThrow(() -> new IllegalArgumentException("Unknown flavor '" + flavorName + "'"));
    }

    /** Returns true if this flavor is configured or can be created on the fly */
    public boolean exists(String flavorName) {
        return getFlavor(flavorName).isPresent();
    }

    private static Collection<Flavor> toFlavors(FlavorsConfig config) {
        return config.flavor().stream().map(Flavor::new).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return String.join(",", configuredFlavors.keySet());
    }

}
