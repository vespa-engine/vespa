// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author bjorncs
 */
public class CapabilitySet implements ToCapabilitySet {

    private static final Logger log = Logger.getLogger(CapabilitySet.class.getName());

    private static final Map<String, CapabilitySet> PREDEFINED = new HashMap<>();


    /* Predefined capability sets */
    public static final CapabilitySet ALL = predefined(
            "vespa.all", Capability.values());
    public static final CapabilitySet TELEMETRY = predefined(
            "vespa.telemetry",
            Capability.CONTENT__STATUS_PAGES, Capability.CONTENT__METRICS_API, Capability.CONTAINER__STATE_API,
            Capability.METRICSPROXY__METRICS_API, Capability.SENTINEL__CONNECTIVITY_CHECK);

    private static final CapabilitySet SHARED_CAPABILITIES_APP_NODE = CapabilitySet.of(
            Capability.LOGSERVER_API, Capability.CONFIGSERVER__CONFIG_API,
            Capability.CONFIGSERVER__FILEDISTRIBUTION_API, Capability.CONFIGPROXY__CONFIG_API,
            Capability.CONFIGPROXY__FILEDISTRIBUTION_API, Capability.SLOBROK__API, TELEMETRY);

    public static final CapabilitySet CONTENT_NODE = predefined(
            "vespa.content_node",
            Capability.CONTENT__STORAGE_API, Capability.CONTENT__DOCUMENT_API, Capability.CONTAINER__DOCUMENT_API,
            SHARED_CAPABILITIES_APP_NODE);
    public static final CapabilitySet CONTAINER_NODE = predefined(
            "vespa.container_node",
            Capability.CONTAINER__DOCUMENT_API, Capability.CONTENT__DOCUMENT_API, Capability.CONTENT__SEARCH_API,
            SHARED_CAPABILITIES_APP_NODE);
    public static final CapabilitySet CLUSTER_CONTROLLER_NODE = predefined(
            "vespa.cluster_controller_node",
            Capability.CONTENT__CLUSTER_CONTROLLER__INTERNAL_STATE_API,
            Capability.CLIENT__SLOBROK_API, Capability.CONTAINER__DOCUMENT_API, SHARED_CAPABILITIES_APP_NODE);
    public static final CapabilitySet LOGSERVER_NODE = predefined(
            "vespa.logserver_node", SHARED_CAPABILITIES_APP_NODE);
    public static final CapabilitySet CONFIGSERVER_NODE = predefined(
            "vespa.config_server_node",
            Capability.CLIENT__FILERECEIVER_API, Capability.CONTAINER__MANAGEMENT_API, Capability.SLOBROK__API,
            Capability.CLUSTER_CONTROLLER__REINDEXING, Capability.CLUSTER_CONTROLLER__STATE, Capability.LOGSERVER_API,
            TELEMETRY);

    private static CapabilitySet predefined(String name, ToCapabilitySet... capabilities) {
        var instance = CapabilitySet.of(capabilities);
        PREDEFINED.put(name, instance);
        return instance;
    }

    private static final CapabilitySet ALL_CAPABILITIES = new CapabilitySet(EnumSet.allOf(Capability.class));
    private static final CapabilitySet NO_CAPABILITIES = new CapabilitySet(EnumSet.noneOf(Capability.class));

    private final EnumSet<Capability> caps;

    private CapabilitySet(EnumSet<Capability> caps) { this.caps = caps; }

    @Override public CapabilitySet toCapabilitySet() { return this; }

    public static CapabilitySet fromNames(Collection<String> names) {
        EnumSet<Capability> caps = EnumSet.noneOf(Capability.class);
        for (String name : names) {
            var predefinedSet = PREDEFINED.get(name);
            var capability = Capability.fromName(name).orElse(null);
            if (capability != null) caps.add(capability);
            else if (predefinedSet != null) caps.addAll(predefinedSet.caps);
            else log.warning("Cannot find capability or capability set with name '%s'".formatted(name));
        }
        return new CapabilitySet(caps);
    }

    public static CapabilitySet unionOf(Collection<CapabilitySet> capSets) {
        EnumSet<Capability> union = EnumSet.noneOf(Capability.class);
        capSets.forEach(cs -> union.addAll(cs.caps));
        return new CapabilitySet(union);
    }

    public static CapabilitySet of(ToCapabilitySet... capabilities) {
        return CapabilitySet.unionOf(Arrays.stream(capabilities).map(ToCapabilitySet::toCapabilitySet).toList());
    }

    public static CapabilitySet of(EnumSet<Capability> caps) { return new CapabilitySet(EnumSet.copyOf(caps)); }
    public static CapabilitySet of(Collection<Capability> caps) { return new CapabilitySet(EnumSet.copyOf(caps)); }
    public static CapabilitySet of(Capability... caps) { return new CapabilitySet(EnumSet.copyOf(List.of(caps))); }
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
