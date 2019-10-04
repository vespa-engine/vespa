// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.yahoo.config.application.api.xml.DeploymentSpecXmlReader;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.Reader;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
                                                                  Notifications.none(),
                                                                  List.of());

    private final List<Step> steps;
    private final Optional<Integer> majorVersion;
    private final String xmlForm;

    public DeploymentSpec(List<Step> steps,
                          Optional<Integer> majorVersion,
                          String xmlForm) {
        this.steps = steps;
        this.majorVersion = majorVersion;
        this.xmlForm = xmlForm;
        validateTotalDelay(steps);
    }

    // TODO: Remove after October 2019
    public DeploymentSpec(Optional<String> globalServiceId, UpgradePolicy upgradePolicy, Optional<Integer> majorVersion,
                          List<ChangeBlocker> changeBlockers, List<Step> steps, String xmlForm,
                          Optional<AthenzDomain> athenzDomain, Optional<AthenzService> athenzService,
                          Notifications notifications,
                          List<Endpoint> endpoints) {
        this(List.of(new DeploymentInstanceSpec(InstanceName.from("default"),
                                                steps,
                                                upgradePolicy,
                                                changeBlockers,
                                                globalServiceId,
                                                athenzDomain,
                                                athenzService,
                                                notifications,
                                                endpoints)),
             majorVersion,
             xmlForm);
    }

    /** Throw an IllegalArgumentException if the total delay exceeds 24 hours */
    private void validateTotalDelay(List<Step> steps) {
        long totalDelaySeconds = steps.stream().mapToLong(step -> (step.delay().getSeconds())).sum();
        if (totalDelaySeconds > Duration.ofHours(24).getSeconds())
            throw new IllegalArgumentException("The total delay specified is " + Duration.ofSeconds(totalDelaySeconds) +
                                               " but max 24 hours is allowed");
    }

    // TODO: Remove after October 2019
    private DeploymentInstanceSpec defaultInstance() {
        if (instances().size() == 1) return (DeploymentInstanceSpec)steps.get(0);
        throw new IllegalArgumentException("This deployment spec does not support the legacy API " +
                                           "as it has multiple instances: " +
                                           instances().stream().map(Step::toString).collect(Collectors.joining(",")));
    }

    // TODO: Remove after October 2019
    public Optional<String> globalServiceId() { return defaultInstance().globalServiceId(); }

    // TODO: Remove after October 2019
    public UpgradePolicy upgradePolicy() { return defaultInstance().upgradePolicy(); }

    /** Returns the major version this application is pinned to, or empty (default) to allow all major versions */
    public Optional<Integer> majorVersion() { return majorVersion; }

    // TODO: Remove after November 2019
    public boolean canUpgradeAt(Instant instant) { return defaultInstance().canUpgradeAt(instant); }

    // TODO: Remove after November 2019
    public boolean canChangeRevisionAt(Instant instant) { return defaultInstance().canChangeRevisionAt(instant); }

    // TODO: Remove after November 2019
    public List<ChangeBlocker> changeBlocker() { return defaultInstance().changeBlocker(); }

    /** Returns the deployment steps of this in the order they will be performed */
    public List<Step> steps() {
        if (steps.size() == 1) return defaultInstance().steps(); // TODO: Remove line after November 2019
        return steps;
    }

    // TODO: Remove after November 2019
    public List<DeclaredZone> zones() {
        return defaultInstance().steps().stream()
                                       .flatMap(step -> step.zones().stream())
                                       .collect(Collectors.toList());
    }

    // TODO: Remove after November 2019
    public Optional<AthenzDomain> athenzDomain() { return defaultInstance().athenzDomain(); }

    // TODO: Remove after November 2019
    public Optional<AthenzService> athenzService(Environment environment, RegionName region) {
        return defaultInstance().athenzService(environment, region);
    }

    // TODO: Remove after November 2019
    public Notifications notifications() { return defaultInstance().notifications(); }

    // TODO: Remove after November 2019
    public List<Endpoint> endpoints() { return defaultInstance().endpoints(); }

    /** Returns the XML form of this spec, or null if it was not created by fromXml, nor is empty */
    public String xmlForm() { return xmlForm; }

    // TODO: Remove after November 2019
    public boolean includes(Environment environment, Optional<RegionName> region) {
        return defaultInstance().deploysTo(environment, region);
    }

    /** Returns the instance step containing the given instance name, or null if not present */
    public DeploymentInstanceSpec instance(String name) {
        return instance(InstanceName.from(name));
    }

    /** Returns the instance step containing the given instance name, or null if not present */
    public DeploymentInstanceSpec instance(InstanceName name) {
        for (Step step : steps) {
            if ( ! (step instanceof DeploymentInstanceSpec)) continue;
            DeploymentInstanceSpec instanceStep = (DeploymentInstanceSpec)step;
            if (instanceStep.name().equals(name))
                return instanceStep;
        }
        return null;
    }

    /** Returns the instance step containing the given instance name, or throws an IllegalArgumentException if not present */
    public DeploymentInstanceSpec requireInstance(String name) {
        return requireInstance(InstanceName.from(name));
    }

    public DeploymentInstanceSpec requireInstance(InstanceName name) {
        DeploymentInstanceSpec instance = instance(name);
        if (instance == null)
            throw new IllegalArgumentException("No instance '" + name + "' in deployment.xml'. Instances: " +
                                               instances().stream().map(spec -> spec.name().toString()).collect(Collectors.joining(",")));
        return instance;
    }

    /** Returns the steps of this which are instances */
    public List<DeploymentInstanceSpec> instances() {
        return steps.stream()
                    .filter(step -> step instanceof DeploymentInstanceSpec).map(DeploymentInstanceSpec.class::cast)
                    .collect(Collectors.toList());
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeploymentSpec other = (DeploymentSpec) o;
        return majorVersion.equals(other.majorVersion) &&
               steps.equals(other.steps) &&
               xmlForm.equals(other.xmlForm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(majorVersion, steps, xmlForm);
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

        /** The delay introduced by this step (beyond the time it takes to execute the step). Default is zero. */
        public Duration delay() { return Duration.ZERO; }

    }

    /** A deployment step which is to wait for some time before progressing to the next step */
    public static class Delay extends Step {
        
        private final Duration duration;
        
        public Delay(Duration duration) {
            this.duration = duration;
        }

        // TODO: Remove after October 2019
        public Duration duration() { return duration; }

        @Override
        public Duration delay() { return duration; }

        @Override
        public boolean deploysTo(Environment environment, Optional<RegionName> region) { return false; }

    }

    /** A deployment step which is to run deployment in a particular zone */
    public static class DeclaredZone extends Step {

        private final Environment environment;
        private final Optional<RegionName> region;
        private final boolean active;
        private final Optional<AthenzService> athenzService;
        private final Optional<String> testerFlavor;

        public DeclaredZone(Environment environment) {
            this(environment, Optional.empty(), false);
        }

        public DeclaredZone(Environment environment, Optional<RegionName> region, boolean active) {
            this(environment, region, active, Optional.empty(), Optional.empty());
        }

        public DeclaredZone(Environment environment, Optional<RegionName> region, boolean active, Optional<AthenzService> athenzService) {
            this(environment, region, active, athenzService, Optional.empty());
        }

        public DeclaredZone(Environment environment, Optional<RegionName> region, boolean active,
                            Optional<AthenzService> athenzService, Optional<String> testerFlavor) {
            if (environment != Environment.prod && region.isPresent())
                throw new IllegalArgumentException("Non-prod environments cannot specify a region");
            if (environment == Environment.prod && region.isEmpty())
                throw new IllegalArgumentException("Prod environments must be specified with a region");
            this.environment = environment;
            this.region = region;
            this.active = active;
            this.athenzService = athenzService;
            this.testerFlavor = testerFlavor;
        }

        public Environment environment() { return environment; }

        /** The region name, or empty if not declared */
        public Optional<RegionName> region() { return region; }

        /** Returns whether this zone should receive production traffic */
        public boolean active() { return active; }

        public Optional<String> testerFlavor() { return testerFlavor; }

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
            return environment + (region.map(regionName -> "." + regionName).orElse(""));
        }

    }

    /** A deployment step which is to run multiple steps (zones or instances) in parallel */
    public static class ParallelZones extends Step {

        private final List<Step> steps;

        public ParallelZones(List<Step> steps) {
            this.steps = List.copyOf(steps);
        }

        /** Returns the steps inside this which are zones */
        @Override
        public List<DeclaredZone> zones() {
            return this.steps.stream()
                             .filter(step -> step instanceof DeclaredZone)
                             .map(DeclaredZone.class::cast)
                             .collect(Collectors.toList());
        }

        /** Returns all the steps nested in this */
        public List<Step> steps() { return steps; }

        @Override
        public boolean deploysTo(Environment environment, Optional<RegionName> region) {
            return zones().stream().anyMatch(zone -> zone.deploysTo(environment, region));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ParallelZones)) return false;
            ParallelZones that = (ParallelZones) o;
            return Objects.equals(steps, that.steps);
        }

        @Override
        public int hashCode() {
            return Objects.hash(steps);
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
