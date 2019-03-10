// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.yahoo.config.provisioning.FlavorsConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * All the available node flavors.
 *
 * @author bratseth
 */
public class NodeFlavors {

    /** Flavors <b>which are configured</b> in this zone */
    private final ImmutableMap<String, Flavor> flavors;

    @Inject
    public NodeFlavors(FlavorsConfig config) {
        ImmutableMap.Builder<String, Flavor> b = new ImmutableMap.Builder<>();
        for (Flavor flavor : toFlavors(config))
            b.put(flavor.name(), flavor);
        this.flavors = b.build();
    }

    public List<Flavor> getFlavors() {
        return new ArrayList<>(flavors.values());
    }

    /** Returns a flavor by name, or empty if there is no flavor with this name. */
    public Optional<Flavor> getFlavor(String name) {
        return Optional.ofNullable(flavors.get(name));
    }

    /** Returns the flavor with the given name or throws an IllegalArgumentException if it does not exist */
    public Flavor getFlavorOrThrow(String flavorName) {
        return getFlavor(flavorName).orElseThrow(() -> new IllegalArgumentException("Unknown flavor '" + flavorName +
                "'. Flavors are " + canonicalFlavorNames()));
    }

    private List<String> canonicalFlavorNames() {
        return flavors.values().stream().map(Flavor::canonicalName).distinct().sorted().collect(Collectors.toList());
    }

    private static Collection<Flavor> toFlavors(FlavorsConfig config) {
        Map<String, Flavor> flavors = new HashMap<>();
        // First pass, create all flavors, but do not include flavorReplacesConfig.
        for (FlavorsConfig.Flavor flavorConfig : config.flavor()) {
            flavors.put(flavorConfig.name(), new Flavor(flavorConfig));
        }
        // Second pass, set flavorReplacesConfig to point to correct flavor.
        for (FlavorsConfig.Flavor flavorConfig : config.flavor()) {
            Flavor flavor = flavors.get(flavorConfig.name());
            for (FlavorsConfig.Flavor.Replaces flavorReplacesConfig : flavorConfig.replaces()) {
                if (! flavors.containsKey(flavorReplacesConfig.name())) {
                    throw new IllegalStateException("Replaces for " + flavor.name() + 
                                                    " pointing to a non existing flavor: " + flavorReplacesConfig.name());
                }
                flavor.replaces().add(flavors.get(flavorReplacesConfig.name()));
            }
            flavor.freeze();
        }
        // Third pass, ensure that retired flavors have a replacement
        for (Flavor flavor : flavors.values()) {
            if (flavor.isRetired() && !hasReplacement(flavors.values(), flavor)) {
                throw new IllegalStateException(
                        String.format("Flavor '%s' is retired, but has no replacement", flavor.name())
                );
            }
        }
        return flavors.values();
    }

    private static boolean hasReplacement(Collection<Flavor> flavors, Flavor flavor) {
        return flavors.stream()
                .filter(f -> !f.equals(flavor))
                .anyMatch(f -> f.satisfies(flavor));
    }

}
