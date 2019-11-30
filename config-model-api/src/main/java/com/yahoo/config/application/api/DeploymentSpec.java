// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.yahoo.collections.Comparables;
import com.yahoo.config.application.api.xml.DeploymentSpecXmlReader;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;

import java.io.Reader;
import java.time.Duration;
import java.util.ArrayList;
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
    public static final DeploymentSpec empty = new DeploymentSpec(List.of(new DeploymentInstanceSpec(InstanceName.from("default"),
                                                                                                     Collections.emptyList(),
                                                                                                     UpgradePolicy.defaultPolicy,
                                                                                                     Collections.emptyList(),
                                                                                                     Optional.empty(),
                                                                                                     Optional.empty(),
                                                                                                     Optional.empty(),
                                                                                                     Notifications.none(),
                                                                                                     List.of())),
                                                                  Optional.empty(),
                                                                  Optional.empty(),
                                                                  Optional.empty(),
                                                                  "<deployment version='1.0'/>");

    private final List<Step> steps;

    // Attributes which can be set on the root tag and which must be available outside of any particular instance
    private final Optional<Integer> majorVersion;
    private final Optional<AthenzDomain> athenzDomain;
    private final Optional<AthenzService> athenzService;

    private final String xmlForm;

    public DeploymentSpec(List<Step> steps,
                          Optional<Integer> majorVersion,
                          Optional<AthenzDomain> athenzDomain,
                          Optional<AthenzService> athenzService,
                          String xmlForm) {
        this.steps = List.copyOf(completeSteps(steps));
        this.majorVersion = majorVersion;
        this.athenzDomain = athenzDomain;
        this.athenzService = athenzService;
        this.xmlForm = xmlForm;
        validateTotalDelay(steps);
        validateUpgradePoliciesOfIncreasingConservativeness(steps);
    }

    /** Adds missing required steps and reorders steps to a permissible order */
    private static List<DeploymentSpec.Step> completeSteps(List<DeploymentSpec.Step> inputSteps) {
        List<Step> steps = new ArrayList<>(inputSteps);

        // Add staging if required and missing
        if (steps.stream().anyMatch(step -> step.deploysTo(Environment.prod)) &&
            steps.stream().noneMatch(step -> step.deploysTo(Environment.staging))) {
            steps.add(new DeploymentSpec.DeclaredZone(Environment.staging));
        }

        // Add test if required and missing
        if (steps.stream().anyMatch(step -> step.deploysTo(Environment.staging)) &&
            steps.stream().noneMatch(step -> step.deploysTo(Environment.test))) {
            steps.add(new DeploymentSpec.DeclaredZone(Environment.test));
        }

        // Enforce order test, staging, prod
        DeploymentSpec.DeclaredZone testStep = remove(Environment.test, steps);
        if (testStep != null)
            steps.add(0, testStep);
        DeploymentSpec.DeclaredZone stagingStep = remove(Environment.staging, steps);
        if (stagingStep != null)
            steps.add(1, stagingStep);

        return steps;
    }

    /**
     * Removes the first occurrence of a deployment step to the given environment and returns it.
     *
     * @return the removed step, or null if it is not present
     */
    private static DeploymentSpec.DeclaredZone remove(Environment environment, List<DeploymentSpec.Step> steps) {
        for (int i = 0; i < steps.size(); i++) {
            if ( ! (steps.get(i) instanceof DeploymentSpec.DeclaredZone)) continue;
            DeploymentSpec.DeclaredZone zoneStep = (DeploymentSpec.DeclaredZone)steps.get(i);
            if (zoneStep.environment() == environment) {
                steps.remove(i);
                return zoneStep;
            }
        }
        return null;
    }

    /** Throw an IllegalArgumentException if the total delay exceeds 24 hours */
    private void validateTotalDelay(List<Step> steps) {
        long totalDelaySeconds = steps.stream().mapToLong(step -> (step.delay().getSeconds())).sum();
        if (totalDelaySeconds > Duration.ofHours(24).getSeconds())
            throw new IllegalArgumentException("The total delay specified is " + Duration.ofSeconds(totalDelaySeconds) +
                                               " but max 24 hours is allowed");
    }

    /** Throws an IllegalArgumentException if any instance has a looser upgrade policy than the previous */
    private void validateUpgradePoliciesOfIncreasingConservativeness(List<Step> steps) {
        UpgradePolicy previous = Collections.min(List.of(UpgradePolicy.values()));
        for (Step step : steps) {
            UpgradePolicy strictest = previous;
            List<DeploymentInstanceSpec> specs = instances(List.of(step));
            for (DeploymentInstanceSpec spec : specs) {
                if (spec.upgradePolicy().compareTo(previous) < 0)
                    throw new IllegalArgumentException("Instance '" + spec.name() + "' cannot have a looser upgrade " +
                                                       "policy than the previous of '" + previous + "'");

                strictest = Comparables.max(strictest, spec.upgradePolicy());
            }
            previous = strictest;
        }
    }

    /** Returns the major version this application is pinned to, or empty (default) to allow all major versions */
    public Optional<Integer> majorVersion() { return majorVersion; }

    /** Returns the deployment steps of this in the order they will be performed */
    public List<Step> steps() {
        return steps;
    }

    /** Returns the Athenz domain set on the root tag, if any */
    public Optional<AthenzDomain> athenzDomain() { return athenzDomain; }

    /** Returns the Athenz service set on the root tag, if any */
    // athenz-service can be overridden on almost all tags, and with legacy mode + standard + environment and region variants
    // + tester application services it gets complicated, but:
    // 1. any deployment outside dev/perf should happen only with declared instances, implicit or not, which means the spec for
    //    that instance should provide the correct service, based on environment and region, and we should not fall back to this; and
    // 2. any deployment to dev/perf can only have the root or instance tags' value for service, which means we can ignore variants; and
    //    a. for single-instance specs the service is always set on the root tag, and deploying under an unknown instance leads here, and
    //    b. for multi-instance specs the root tag may or may not have a service, and unknown instances also lead here; and
    // 3. any tester application deployment is always an unknown instance, and always gets here, but there should not be any reason
    //    to have environment, instance or region variants on those.
    public Optional<AthenzService> athenzService() { return this.athenzService; }

    // TODO remove when 7.135 is the oldest version
    public Optional<AthenzService> athenzService(InstanceName instanceName, Environment environment, RegionName region) {
        Optional<DeploymentInstanceSpec> instance = instance(instanceName);
        if (instance.isEmpty()) return this.athenzService;
        return instance.get().athenzService(environment, region).or(() -> this.athenzService);
    }

    /** Returns the XML form of this spec, or null if it was not created by fromXml, nor is empty */
    public String xmlForm() { return xmlForm; }

    /** Returns the instance step containing the given instance name */
    public Optional<DeploymentInstanceSpec> instance(InstanceName name) {
        for (DeploymentInstanceSpec instance : instances()) {
            if (instance.name().equals(name))
                return Optional.of(instance);
        }
        return Optional.empty();
    }

    public DeploymentInstanceSpec requireInstance(String name) {
        return requireInstance(InstanceName.from(name));
    }

    public DeploymentInstanceSpec requireInstance(InstanceName name) {
        Optional<DeploymentInstanceSpec> instance = instance(name);
        if (instance.isEmpty())
            throw new IllegalArgumentException("No instance '" + name + "' in deployment.xml'. Instances: " +
                                               instances().stream().map(spec -> spec.name().toString()).collect(Collectors.joining(",")));
        return instance.get();
    }

    /** Returns the instance names declared in this */
    public List<InstanceName> instanceNames() {
        return instances().stream().map(DeploymentInstanceSpec::name).collect(Collectors.toUnmodifiableList());
    }

    /** Returns the step descendants of this which are instances */
    public List<DeploymentInstanceSpec> instances() {
        return instances(steps);
    }

    private static List<DeploymentInstanceSpec> instances(List<DeploymentSpec.Step> steps) {
        return steps.stream()
                    .flatMap(step -> step instanceof ParallelZones ? ((ParallelZones) step).steps.stream() : List.of(step).stream())
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

        /** Returns all the steps nested in this. This default implementatiino returns an empty list. */
        public List<Step> steps() { return List.of(); }

    }

    /** A deployment step which is to wait for some time before progressing to the next step */
    public static class Delay extends Step {
        
        private final Duration duration;
        
        public Delay(Duration duration) {
            this.duration = duration;
        }

        @Override
        public Duration delay() { return duration; }

        @Override
        public boolean deploysTo(Environment environment, Optional<RegionName> region) { return false; }

        @Override
        public String toString() {
            return "delay " + duration;
        }

    }

    /** A deployment step which is to run deployment in a particular zone */
    public static class DeclaredZone extends Step {

        private final Environment environment;
        private final Optional<RegionName> region;
        private final boolean active;
        private final Optional<AthenzService> athenzService;
        private final Optional<String> testerFlavor;

        public DeclaredZone(Environment environment) {
            this(environment, Optional.empty(), false, Optional.empty(), Optional.empty());
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
        @Override
        public List<Step> steps() { return steps; }

        @Override
        public boolean deploysTo(Environment environment, Optional<RegionName> region) {
            return steps().stream().anyMatch(zone -> zone.deploysTo(environment, region));
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

        @Override
        public String toString() {
            return steps.size() + " parallel steps";
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

        @Override
        public String toString() {
            return "change blocker revision=" + revision + " version=" + version + " window=" + window;
        }
        
    }


}
