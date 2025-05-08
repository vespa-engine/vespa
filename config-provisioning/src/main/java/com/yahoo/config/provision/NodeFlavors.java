// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provisioning.FlavorsConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        Map<String, Flavor> map = new LinkedHashMap<>();
        for (Flavor flavor : flavors)
            map.put(flavor.name(), flavor);
        configuredFlavors = Collections.unmodifiableMap(map);
    }

    public List<Flavor> getFlavors() {
        return new ArrayList<>(configuredFlavors.values());
    }

    /** Returns a flavor by name, or empty if there is no flavor with this name. */
    public Optional<Flavor> getFlavor(String name) {
        return Optional.ofNullable(configuredFlavors.get(name));
    }

    /**
     * Returns the flavor with the given name or throws an IllegalArgumentException if it does not exist.
     */
    public Flavor getFlavorOrThrow(String flavorName) {
        return getFlavor(flavorName).orElseThrow(() -> new IllegalArgumentException("Unknown flavor '" + flavorName + "'"));
    }

    /** Returns true if this flavor is configured or can be created on the fly */
    public boolean exists(String flavorName) {
        return getFlavor(flavorName).isPresent();
    }

    private static Collection<Flavor> toFlavors(FlavorsConfig config) {
        return config.flavor().stream().map(Flavor::new).toList();
    }

    @Override
    public String toString() {
        return String.join(",", configuredFlavors.keySet());
    }

}
