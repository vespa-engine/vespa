// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.google.common.collect.ImmutableList;
import com.yahoo.config.application.api.xml.DeploymentSpecXmlReader;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.Reader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Specifies the environments and regions to which an application should be deployed.
 * This may be used both for inspection as part of an application model and to answer
 * queries about deployment from the command line. A main method is included for the latter usage.
 * 
 * A deployment consists of a number of steps executed in the order listed in deployment xml, as
 * well as some additional settings.
 * 
 * This is immutable.
 *
 * @author bratseth
 */
public class DeploymentSpec {

    /** The empty deployment spec, specifying no zones or rotation, and defaults for all settings */
    public static final DeploymentSpec empty = new DeploymentSpec(Optional.empty(),
                                                                  UpgradePolicy.defaultPolicy,
                                                                  Optional.empty(),
                                                                  Collections.emptyList(),
                                                                  Collections.emptyList(),
                                                                  "<deployment version='1.0'/>",
                                                                  Optional.empty(),
                                                                  Optional.empty(),
                                                                  Notifications.none());
    
    private final Optional<String> globalServiceId;
    private final UpgradePolicy upgradePolicy;
    private final Optional<Integer> majorVersion;
    private final List<ChangeBlocker> changeBlockers;
    private final List<Step> steps;
    private final String xmlForm;
    private final Optional<AthenzDomain> athenzDomain;
    private final Optional<AthenzService> athenzService;
    private final Notifications notifications;

    public DeploymentSpec(Optional<String> globalServiceId, UpgradePolicy upgradePolicy, Optional<Integer> majorVersion,
                          List<ChangeBlocker> changeBlockers, List<Step> steps, String xmlForm,
                          Optional<AthenzDomain> athenzDomain, Optional<AthenzService> athenzService, Notifications notifications) {
        validateTotalDelay(steps);
        this.globalServiceId = globalServiceId;
        this.upgradePolicy = upgradePolicy;
        this.majorVersion = majorVersion;
        this.changeBlockers = changeBlockers;
        this.steps = ImmutableList.copyOf(completeSteps(new ArrayList<>(steps)));
        this.xmlForm = xmlForm;
        this.athenzDomain = athenzDomain;
        this.athenzService = athenzService;
        this.notifications = notifications;
        validateZones(this.steps);
        validateAthenz();
    }
    
    /** Throw an IllegalArgumentException if the total delay exceeds 24 hours */
    private void validateTotalDelay(List<Step> steps) {
        long totalDelaySeconds = steps.stream().filter(step -> step instanceof Delay)
                                               .mapToLong(delay -> ((Delay)delay).duration().getSeconds())
                                               .sum();
        if (totalDelaySeconds > Duration.ofHours(24).getSeconds())
            throw new IllegalArgumentException("The total delay specified is " + Duration.ofSeconds(totalDelaySeconds) +
                                               " but max 24 hours is allowed");
    }

    /** Throw an IllegalArgumentException if any production zone is declared multiple times */
    private void validateZones(List<Step> steps) {
        Set<DeclaredZone> zones = new HashSet<>();

        for (Step step : steps)
            for (DeclaredZone zone : step.zones())
                ensureUnique(zone, zones);
    }

    /*
     * Throw an IllegalArgumentException if Athenz configuration violates:
     * domain not configured -> no zone can configure service
     * domain configured -> all zones must configure service
     */
    private void validateAthenz() {
        // If athenz domain is not set, athenz service cannot be set on any level
        if (! athenzDomain.isPresent()) {
            for (DeclaredZone zone : zones()) {
                if(zone.athenzService().isPresent()) {
                    throw new IllegalArgumentException("Athenz service configured for zone: " + zone + ", but Athenz domain is not configured");
                }
            }
        // if athenz domain is not set, athenz service must be set implicitly or directly on all zones.
        } else if(! athenzService.isPresent()) {
            for (DeclaredZone zone : zones()) {
                if(! zone.athenzService().isPresent()) {
                    throw new IllegalArgumentException("Athenz domain is configured, but Athenz service not configured for zone: " + zone);
                }
            }
        }
    }

    private void ensureUnique(DeclaredZone zone, Set<DeclaredZone> zones) {
        if ( ! zones.add(zone))
            throw new IllegalArgumentException(zone + " is listed twice in deployment.xml");
    }

    /** Adds missing required steps and reorders steps to a permissible order */
    private static List<Step> completeSteps(List<Step> steps) {
        // Add staging if required and missing
        if (steps.stream().anyMatch(step -> step.deploysTo(Environment.prod)) &&
            steps.stream().noneMatch(step -> step.deploysTo(Environment.staging))) {
            steps.add(new DeclaredZone(Environment.staging));
        }
        
        // Add test if required and missing
        if (steps.stream().anyMatch(step -> step.deploysTo(Environment.staging)) &&
            steps.stream().noneMatch(step -> step.deploysTo(Environment.test))) {
            steps.add(new DeclaredZone(Environment.test));
        }
        
        // Enforce order test, staging, prod
        DeclaredZone testStep = remove(Environment.test, steps);
        if (testStep != null)
            steps.add(0, testStep);
        DeclaredZone stagingStep = remove(Environment.staging, steps);
        if (stagingStep != null)
            steps.add(1, stagingStep);
        
        return steps;
    }

    /** 
     * Removes the first occurrence of a deployment step to the given environment and returns it.
     * 
     * @return the removed step, or null if it is not present
     */
    private static DeclaredZone remove(Environment environment, List<Step> steps) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).deploysTo(environment))
                return (DeclaredZone)steps.remove(i);
        }
        return null;
    }

    /** Returns the ID of the service to expose through global routing, if present */
    public Optional<String> globalServiceId() {
        return globalServiceId;
    }

    /** Returns the upgrade policy of this, which is defaultPolicy if none is specified */
    public UpgradePolicy upgradePolicy() { return upgradePolicy; }

    /** Returns the major version this application is pinned to, or empty (default) to allow all major versions */
    public Optional<Integer> majorVersion() { return majorVersion; }

    /** Returns whether upgrade can occur at the given instant */
    public boolean canUpgradeAt(Instant instant) {
        return changeBlockers.stream().filter(block -> block.blocksVersions())
                                      .noneMatch(block -> block.window().includes(instant));
    }

    /** Returns whether an application revision change can occur at the given instant */
    public boolean canChangeRevisionAt(Instant instant) {
        return changeBlockers.stream().filter(block -> block.blocksRevisions())
                                      .noneMatch(block -> block.window().includes(instant));
    }

    /** Returns time windows where upgrades are disallowed */
    public List<ChangeBlocker> changeBlocker() { return changeBlockers; }

    /** Returns the deployment steps of this in the order they will be performed */
    public List<Step> steps() { return steps; }

    /** Returns all the DeclaredZone deployment steps in the order they are declared */
    public List<DeclaredZone> zones() {
        return steps.stream()
                .flatMap(step -> step.zones().stream())
                .collect(Collectors.toList());
    }

    /** Returns the notification configuration */
    public Notifications notifications() { return notifications; }

    /** Returns the XML form of this spec, or null if it was not created by fromXml, nor is empty */
    public String xmlForm() { return xmlForm; }

    /** Returns whether this deployment spec specifies the given zone, either implicitly or explicitly */
    public boolean includes(Environment environment, Optional<RegionName> region) {
        for (Step step : steps)
            if (step.deploysTo(environment, region)) return true;
        return false;
    }

    /**
     * Creates a deployment spec from XML.
     *
     * @throws IllegalArgumentException if the XML is invalid
     */
    public static DeploymentSpec fromXml(Reader reader) {
        return new DeploymentSpecXmlReader().read(reader);
    }

    /**
     * Creates a deployment spec from XML.
     *
     * @throws IllegalArgumentException if the XML is invalid
     */
    public static DeploymentSpec fromXml(String xmlForm) {
        return fromXml(xmlForm, true);
    }

    /**
     * Creates a deployment spec from XML.
     *
     * @throws IllegalArgumentException if the XML is invalid
     */
    public static DeploymentSpec fromXml(String xmlForm, boolean validate) {
        return new DeploymentSpecXmlReader(validate).read(xmlForm);
    }

    public static String toMessageString(Throwable t) {
        StringBuilder b = new StringBuilder();
        String lastMessage = null;
        String message;
        for (; t != null; t = t.getCause()) {
            message = t.getMessage();
            if (message == null) continue;
            if (message.equals(lastMessage)) continue;
            if (b.length() > 0) {
                b.append(": ");
            }
            b.append(message);
            lastMessage = message;
        }
        return b.toString();
    }

    /** Returns the athenz domain if configured */
    public Optional<AthenzDomain> athenzDomain() {
        return athenzDomain;
    }

    /** Returns the athenz service for environment/region if configured */
    public Optional<AthenzService> athenzService(Environment environment, RegionName region) {
        AthenzService athenzService = zones().stream()
                .filter(zone -> zone.deploysTo(environment, Optional.of(region)))
                .findFirst()
                .flatMap(DeclaredZone::athenzService)
                .orElse(this.athenzService.orElse(null));
        return Optional.ofNullable(athenzService);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeploymentSpec that = (DeploymentSpec) o;
        return globalServiceId.equals(that.globalServiceId) &&
               upgradePolicy == that.upgradePolicy &&
               majorVersion.equals(that.majorVersion) &&
               changeBlockers.equals(that.changeBlockers) &&
               steps.equals(that.steps) &&
               xmlForm.equals(that.xmlForm) &&
               athenzDomain.equals(that.athenzDomain) &&
               athenzService.equals(that.athenzService) &&
               notifications.equals(that.notifications);
    }

    @Override
    public int hashCode() {
        return Objects.hash(globalServiceId, upgradePolicy, majorVersion, changeBlockers, steps, xmlForm, athenzDomain, athenzService, notifications);
    }

    /** This may be invoked by a continuous build */
    public static void main(String[] args) {
        if (args.length != 2 && args.length != 3) {
            System.err.println("Usage: DeploymentSpec [file] [environment] [region]?" +
                               "Returns 0 if the specified zone matches the deployment spec, 1 otherwise");
            System.exit(1);
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(args[0]))) {
            DeploymentSpec spec = DeploymentSpec.fromXml(reader);
            Environment environment = Environment.from(args[1]);
            Optional<RegionName> region = args.length == 3 ? Optional.of(RegionName.from(args[2])) : Optional.empty();
            if (spec.includes(environment, region))
                System.exit(0);
            else
                System.exit(1);
        }
        catch (Exception e) {
            System.err.println("Exception checking deployment spec: " + toMessageString(e));
            System.exit(1);
        }
    }

    /** A deployment step */
    public abstract static class Step {
        
        /** Returns whether this step deploys to the given region */
        public final boolean deploysTo(Environment environment) {
            return deploysTo(environment, Optional.empty());
        }

        /** Returns whether this step deploys to the given environment, and (if specified) region */
        public abstract boolean deploysTo(Environment environment, Optional<RegionName> region);

        /** Returns the zones deployed to in this step */
        public List<DeclaredZone> zones() { return Collections.emptyList(); }

    }

    /** A deployment step which is to wait for some time before progressing to the next step */
    public static class Delay extends Step {
        
        private final Duration duration;
        
        public Delay(Duration duration) {
            this.duration = duration;
        }
        
        public Duration duration() { return duration; }

        @Override
        public boolean deploysTo(Environment environment, Optional<RegionName> region) { return false; }

    }

    /** A deployment step which is to run deployment in a particular zone */
    public static class DeclaredZone extends Step {

        private final Environment environment;

        private Optional<RegionName> region;

        private final boolean active;

        private Optional<AthenzService> athenzService;

        public DeclaredZone(Environment environment) {
            this(environment, Optional.empty(), false);
        }

        public DeclaredZone(Environment environment, Optional<RegionName> region, boolean active) {
            this(environment, region, active, Optional.empty());
        }

        public DeclaredZone(Environment environment, Optional<RegionName> region, boolean active, Optional<AthenzService> athenzService) {
            if (environment != Environment.prod && region.isPresent())
                throw new IllegalArgumentException("Non-prod environments cannot specify a region");
            if (environment == Environment.prod && ! region.isPresent())
                throw new IllegalArgumentException("Prod environments must be specified with a region");
            this.environment = environment;
            this.region = region;
            this.active = active;
            this.athenzService = athenzService;
        }

        public Environment environment() { return environment; }

        /** The region name, or empty if not declared */
        public Optional<RegionName> region() { return region; }

        /** Returns whether this zone should receive production traffic */
        public boolean active() { return active; }

        public Optional<AthenzService> athenzService() { return athenzService; }

        @Override
        public List<DeclaredZone> zones() { return Collections.singletonList(this); }

        @Override
        public boolean deploysTo(Environment environment, Optional<RegionName> region) {
            if (environment != this.environment) return false;
            if (region.isPresent() && ! region.equals(this.region)) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(environment, region);
        }
        
        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if ( !  (o instanceof DeclaredZone)) return false;
            DeclaredZone other = (DeclaredZone)o;
            if (this.environment != other.environment) return false;
            if ( ! this.region.equals(other.region())) return false;
            return true;
        }
        
        @Override
        public String toString() {
            return environment + ( region.isPresent() ? "." + region.get() : "");
        }

    }

    /** A deployment step which is to run deployment to multiple zones in parallel */
    public static class ParallelZones extends Step {

        private final List<DeclaredZone> zones;

        public ParallelZones(List<DeclaredZone> zones) {
            this.zones = ImmutableList.copyOf(zones);
        }

        @Override
        public List<DeclaredZone> zones() { return this.zones; }

        @Override
        public boolean deploysTo(Environment environment, Optional<RegionName> region) {
            return zones.stream().anyMatch(zone -> zone.deploysTo(environment, region));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ParallelZones)) return false;
            ParallelZones that = (ParallelZones) o;
            return Objects.equals(zones, that.zones);
        }

        @Override
        public int hashCode() {
            return Objects.hash(zones);
        }
    }

    /** Controls when this application will be upgraded to new Vespa versions */
    public enum UpgradePolicy {
        /** Canary: Applications with this policy will upgrade before any other */
        canary,
        /** Default: Will upgrade after all canary applications upgraded successfully. The default. */
        defaultPolicy,
        /** Will upgrade after most default applications upgraded successfully */
        conservative
    }

    /** A blocking of changes in a given time window */
    public static class ChangeBlocker {
        
        private final boolean revision;
        private final boolean version;
        private final TimeWindow window;
        
        public ChangeBlocker(boolean revision, boolean version, TimeWindow window) {
            this.revision = revision;
            this.version = version;
            this.window = window;
        }
        
        public boolean blocksRevisions() { return revision; }
        public boolean blocksVersions() { return version; }
        public TimeWindow window() { return window; }
        
    }


}
