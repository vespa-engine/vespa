// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.internal;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provisioning.FlavorsConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link NodeFlavors} generated from config
 *
 * @author bratseth
 */
public class ConfigNodeFlavors implements NodeFlavors {

    /** Flavors <b>which are configured</b> in this zone */
    private final Map<String, Flavor> flavors;

    @Inject
    public ConfigNodeFlavors(FlavorsConfig config) {
        this(toFlavors(config));
    }

    public ConfigNodeFlavors(Collection<Flavor> flavors) {
        ImmutableMap.Builder<String, Flavor> b = new ImmutableMap.Builder<>();
        for (Flavor flavor : flavors)
            b.put(flavor.flavorName(), flavor);
        this.flavors = b.build();
    }

    public List<Flavor> getFlavors() {
        return new ArrayList<>(flavors.values());
    }

    /** Returns a flavor by name, or empty if there is no flavor with this name. */
    public Optional<Flavor> getFlavor(String name) {
        return Optional.ofNullable(flavors.get(name));
    }

    private static Collection<Flavor> toFlavors(FlavorsConfig config) {
        Map<String, Flavor> flavors = new HashMap<>();
        // First pass, create all flavors, but do not include flavorReplacesConfig.
        for (FlavorsConfig.Flavor flavorConfig : config.flavor()) {
            flavors.put(flavorConfig.name(), new ConfigFlavor(flavorConfig));
        }
        // Second pass, set flavorReplacesConfig to point to correct flavor.
        for (FlavorsConfig.Flavor flavorConfig : config.flavor()) {
            Flavor flavor = flavors.get(flavorConfig.name());
            for (FlavorsConfig.Flavor.Replaces flavorReplacesConfig : flavorConfig.replaces()) {
                if (! flavors.containsKey(flavorReplacesConfig.name())) {
                    throw new IllegalStateException("Replaces for " + flavor.flavorName() +
                                                    " pointing to a non existing flavor: " + flavorReplacesConfig.name());
                }
                flavor.replaces().add(flavors.get(flavorReplacesConfig.name()));
            }
            ((ConfigFlavor) flavor).freeze();
        }
        // Third pass, ensure that retired flavors have a replacement
        for (Flavor flavor : flavors.values()) {
            if (flavor.isRetired() && !hasReplacement(flavors.values(), flavor)) {
                throw new IllegalStateException(
                        String.format("Flavor '%s' is retired, but has no replacement", flavor.flavorName())
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
