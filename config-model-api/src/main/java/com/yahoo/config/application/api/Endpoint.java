// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Represents an application- or instance-level endpoint in deployments.xml.
 *
 * - An instance-level endpoint is global and can span multiple regions within a single instance.
 * - An application-level endpoint points can span multiple instances within a single region.
 *
 * @author ogronnesby
 * @author mpolden
 */
public class Endpoint {

    public static final String DEFAULT_ID = "default";

    /*
     * Endpoint IDs must be:
     * - lowercase
     * - alphanumeric
     * - begin with a character
     * - contain zero consecutive dashes
     * - have a length between 1 and 12
     */
    private static final Pattern endpointPattern = Pattern.compile("^[a-z](?:-?[a-z0-9]+)*$");
    private static final int endpointMaxLength = 12;

    private final String endpointId;
    private final String containerId;
    private final Level level;
    private final List<Target> targets;

    public Endpoint(String endpointId, String containerId, Level level, List<Target> targets) {
        this.endpointId = Objects.requireNonNull(endpointId, "endpointId must be non-null");
        this.containerId = Objects.requireNonNull(containerId, "containerId must be non-null");
        this.level = Objects.requireNonNull(level, "level must be non-null");
        this.targets = List.copyOf(Objects.requireNonNull(targets, "targets must be non-null"));
        if (endpointId().length() > endpointMaxLength || !endpointPattern.matcher(endpointId()).matches()) {
            throw new IllegalArgumentException("Invalid endpoint ID: '" + endpointId() + "'");
        }
        if (targets.isEmpty()) throw new IllegalArgumentException("targets must be non-empty");
        for (int i = 0; i < targets.size(); i++) {
            for (int j = 0; j < i; j++) {
                Target a = targets.get(i);
                Target b = targets.get(j);
                if (level == Level.application) {
                    // - All instance name and region combinations must be distinct
                    if (a.instance().equals(b.instance()) && a.region.equals(b.region))
                        throw new IllegalArgumentException("Instance '" + a.instance + "' declared multiple times " +
                                                           "with region '" + a.region + "', but allowed at most once");
                }
                if (level == Level.instance && a.region.equals(b.region)) {
                    // - Instance name is implicit
                    // - All regions must be distinct
                    throw new IllegalArgumentException("Region '" + a.region + "' declared multiple times, but allowed at most once");
                }
            }
        }
    }

    /** The unique identifer of this */
    public String endpointId() {
        return endpointId;
    }

    /** The container cluster this points to */
    public String containerId() {
        return containerId;
    }

    /** The regions this points to */
    public List<RegionName> regions() {
        return targets.stream().map(Target::region).toList();
    }

    /** The level of targets in this */
    public Level level() {
        return level;
    }

    /** The targets this points to */
    public List<Target> targets() {
        return targets;
    }

    /** Returns a copy of this with targets set to given targets */
    public Endpoint withTargets(List<Target> targets) {
        return new Endpoint(endpointId, containerId, level, targets);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Endpoint endpoint = (Endpoint) o;
        return endpointId.equals(endpoint.endpointId) && containerId.equals(endpoint.containerId) && level == endpoint.level && targets.equals(endpoint.targets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpointId, containerId, level, targets);
    }

    @Override
    public String toString() {
        if (level == Level.application) {
            return "endpoint '" + endpointId() + "' (cluster " + containerId + ") -> " +
                   targets.stream().map(Target::toString).sorted()
                          .collect(Collectors.joining(", "));
        }
        return "endpoint '" + endpointId() + "' (cluster " + containerId + ") -> " +
               targets.stream().map(Target::region).map(RegionName::value).sorted()
                      .collect(Collectors.joining(", "));
    }

    /** The level of targets in an endpoint */
    public enum Level {
        application,
        instance,
    }

    /** A target of an endpoint */
    public static class Target {

        private final RegionName region;
        private final InstanceName instance;
        private final int weight;

        public Target(RegionName region, InstanceName instance, int weight) {
            this.region = Objects.requireNonNull(region);
            this.instance = Objects.requireNonNull(instance);
            this.weight = weight;
            if (weight < 0 || weight > 100) {
                throw new IllegalArgumentException("Target must have weight in range [0, 100], got " + weight);
            }
        }

        /** The region this points to */
        public RegionName region() {
            return region;
        }

        /** The instance this points to */
        public InstanceName instance() {
            return instance;
        }

        /** The routing weight of this target */
        public int weight() {
            return weight;
        }

        @Override
        public String toString() {
            return "region=" + region + ",instance=" + instance + ",weight=" + weight;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Target target = (Target) o;
            return weight == target.weight && region.equals(target.region) && instance.equals(target.instance);
        }

        @Override
        public int hashCode() {
            return Objects.hash(region, instance, weight);
        }

    }

}
