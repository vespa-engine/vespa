// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * @author bjorncs
 */
public class CapabilitySet {
    public enum Predefined {
        CONTENT_NODE("vespa.content_node",
                Capability.CONTENT__STORAGE_API, Capability.CONTENT__DOCUMENT_API, Capability.SLOBROK__API),
        CONTAINER_NODE("vespa.container_node",
                Capability.CONTENT__DOCUMENT_API, Capability.CONTENT__SEARCH_API, Capability.SLOBROK__API),
        TELEMETRY("vespa.telemetry",
                Capability.CONTENT__STATUS_PAGES, Capability.CONTENT__METRICS_API),
        CLUSTER_CONTROLLER_NODE("vespa.cluster_controller_node",
                Capability.CONTENT__CLUSTER_CONTROLLER__INTERNAL_STATE_API, Capability.SLOBROK__API),
        CONFIG_SERVER("vespa.config_server"),
        ;

        private final String name;
        private final CapabilitySet set;

        Predefined(String name, Capability... caps) {
            this.name = name;
            this.set = caps.length == 0 ? CapabilitySet.none() : CapabilitySet.from(caps); }

        public static Optional<Predefined> fromName(String name) {
            return Arrays.stream(values()).filter(p -> p.name.equals(name)).findAny();
        }

        public CapabilitySet capabilities() { return set; }
    }

    private static final CapabilitySet ALL_CAPABILITIES = new CapabilitySet(EnumSet.allOf(Capability.class));
    private static final CapabilitySet NO_CAPABILITIES = new CapabilitySet(EnumSet.noneOf(Capability.class));

    private final EnumSet<Capability> caps;

    private CapabilitySet(EnumSet<Capability> caps) { this.caps = caps; }

    public static CapabilitySet fromNames(Collection<String> names) {
        EnumSet<Capability> caps = EnumSet.noneOf(Capability.class);
        for (String name : names) {
            Predefined predefined = Predefined.fromName(name).orElse(null);
            if (predefined != null) caps.addAll(predefined.set.caps);
            else caps.add(Capability.fromName(name));
        }
        return new CapabilitySet(caps);
    }

    public static CapabilitySet unionOf(Collection<CapabilitySet> capSets) {
        EnumSet<Capability> union = EnumSet.noneOf(Capability.class);
        capSets.forEach(cs -> union.addAll(cs.caps));
        return new CapabilitySet(union);
    }

    public static CapabilitySet from(EnumSet<Capability> caps) { return new CapabilitySet(EnumSet.copyOf(caps)); }
    public static CapabilitySet from(Collection<Capability> caps) { return new CapabilitySet(EnumSet.copyOf(caps)); }
    public static CapabilitySet from(Capability... caps) { return new CapabilitySet(EnumSet.copyOf(List.of(caps))); }
    public static CapabilitySet all() { return ALL_CAPABILITIES; }
    public static CapabilitySet none() { return NO_CAPABILITIES; }

    public boolean hasAll() { return this.caps.equals(ALL_CAPABILITIES.caps); }
    public boolean hasNone() { return this.caps.equals(NO_CAPABILITIES.caps); }
    public boolean has(CapabilitySet caps) { return this.caps.containsAll(caps.caps); }
    public boolean has(Collection<Capability> caps) { return this.caps.containsAll(caps); }
    public boolean has(Capability... caps) {  return this.caps.containsAll(List.of(caps)); }

    public SortedSet<String> toNames() {
        return caps.stream().map(Capability::asString).collect(Collectors.toCollection(TreeSet::new));
    }

    public Set<Capability> asSet() { return Collections.unmodifiableSet(caps); }

    @Override
    public String toString() {
        return "CapabilitySet{" +
                "caps=" + caps +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CapabilitySet that = (CapabilitySet) o;
        return Objects.equals(caps, that.caps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caps);
    }
}
