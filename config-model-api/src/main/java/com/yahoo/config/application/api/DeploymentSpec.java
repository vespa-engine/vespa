// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.yahoo.collections.Comparables;
import com.yahoo.config.application.api.xml.DeploymentSpecXmlReader;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.ZoneEndpoint;
import com.yahoo.config.provision.zone.ZoneId;

import java.io.Reader;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    public static final DeploymentSpec empty = new DeploymentSpec(List.of(),
                                                                  Optional.empty(),
                                                                  Optional.empty(),
                                                                  Optional.empty(),
                                                                  Optional.empty(),
                                                                  List.of(),
                                                                  "<deployment version='1.0'/>",
                                                                  List.of());

    private final List<Step> steps;

    // Attributes which can be set on the root tag and which must be available outside any particular instance
    private final Optional<Integer> majorVersion;
    private final Optional<AthenzDomain> athenzDomain;
    private final Optional<AthenzService> athenzService;
    private final Optional<CloudAccount> cloudAccount;
    private final List<Endpoint> endpoints;
    private final List<DeprecatedElement> deprecatedElements;

    private final String xmlForm;

    public DeploymentSpec(List<Step> steps,
                          Optional<Integer> majorVersion,
                          Optional<AthenzDomain> athenzDomain,
                          Optional<AthenzService> athenzService,
                          Optional<CloudAccount> cloudAccount,
                          List<Endpoint> endpoints,
                          String xmlForm,
                          List<DeprecatedElement> deprecatedElements) {
        this.steps = List.copyOf(Objects.requireNonNull(steps));
        this.majorVersion = Objects.requireNonNull(majorVersion);
        this.athenzDomain = Objects.requireNonNull(athenzDomain);
        this.athenzService = Objects.requireNonNull(athenzService);
        this.cloudAccount = Objects.requireNonNull(cloudAccount);
        this.xmlForm = Objects.requireNonNull(xmlForm);
        this.endpoints = List.copyOf(Objects.requireNonNull(endpoints));
        this.deprecatedElements = List.copyOf(Objects.requireNonNull(deprecatedElements));
        validateTotalDelay(steps);
        validateUpgradePoliciesOfIncreasingConservativeness(steps);
        validateAthenz();
        validateApplicationEndpoints();
    }

    public boolean isEmpty() { return this == empty; }

    /** Throw an IllegalArgumentException if the total delay exceeds 24 hours */
    private void validateTotalDelay(List<Step> steps) {
        long totalDelaySeconds = steps.stream().mapToLong(step -> (step.delay().getSeconds())).sum();
        if (totalDelaySeconds > Duration.ofHours(48).getSeconds())
            illegal("The total delay specified is " + Duration.ofSeconds(totalDelaySeconds) +
                    " but max 48 hours is allowed");
    }

    /** Throws an IllegalArgumentException if any instance has a looser upgrade policy than the previous */
    private void validateUpgradePoliciesOfIncreasingConservativeness(List<Step> steps) {
        UpgradePolicy previous = Collections.min(List.of(UpgradePolicy.values()));
        for (Step step : steps) {
            UpgradePolicy strictest = previous;
            List<DeploymentInstanceSpec> specs = instances(List.of(step));
            for (DeploymentInstanceSpec spec : specs) {
                if (spec.upgradePolicy().compareTo(previous) < 0)
                    illegal("Instance '" + spec.name() + "' cannot have a looser upgrade " +
                            "policy than the previous of '" + previous + "'");

                strictest = Comparables.max(strictest, spec.upgradePolicy());
            }
            previous = strictest;
        }
    }

    /**
     * Throw an IllegalArgumentException if Athenz configuration violates:
     * domain not configured -> no zone can configure service
     * domain configured -> all zones must configure service
     */
    private void validateAthenz() {
        // If athenz domain is not set, athenz service cannot be set on any level
        if (athenzDomain.isEmpty()) {
            for (DeploymentInstanceSpec instance : instances()) {
                for (DeploymentSpec.DeclaredZone zone : instance.zones()) {
                    if (zone.athenzService().isPresent()) {
                        illegal("Athenz service configured for zone: " + zone + ", but Athenz domain is not configured");
                    }
                }
            }
            // if athenz domain is not set, athenz service must be set implicitly or directly on all zones.
        }
        else if (athenzService.isEmpty()) {
            for (DeploymentInstanceSpec instance : instances()) {
                for (DeploymentSpec.DeclaredZone zone : instance.zones()) {
                    if (zone.athenzService().isEmpty()) {
                        illegal("Athenz domain is configured, but Athenz service not configured for zone: " + zone);
                    }
                }
            }
        }
    }

    private void validateApplicationEndpoints() {
        for (var endpoint : endpoints) {
            if (endpoint.level() != Endpoint.Level.application) illegal("Endpoint '" + endpoint.endpointId() + "' must be an application–level endpoint, got " + endpoint.level());
            String prefix = "Application-level endpoint '" + endpoint.endpointId() + "': ";
            for (var target : endpoint.targets()) {
                Optional<DeploymentInstanceSpec> instance = instance(target.instance());
                if (instance.isEmpty()) {
                    illegal(prefix + "targets undeclared instance '" + target.instance() + "'");
                }
                if (!instance.get().deploysTo(Environment.prod, target.region())) {
                    illegal(prefix + "targets undeclared region '" + target.region() +
                            "' in instance '" + target.instance() + "'");
                }
                if (instance.get().zoneEndpoint(ZoneId.from(Environment.prod, target.region()), ClusterSpec.Id.from(endpoint.containerId()))
                            .map(zoneEndpoint -> ! zoneEndpoint.isPublicEndpoint()).orElse(false))
                    illegal(prefix + "targets '" + target.region().value() + "' in '" + target.instance().value() +
                            "', but its zone endpoint has 'enabled' set to 'false'");
            }
        }
    }

    /** Returns the major version this application is pinned to, or empty (default) to allow all major versions */
    public Optional<Integer> majorVersion() { return majorVersion; }

    /** Returns the deployment steps of this in the order they will be performed */
    public List<Step> steps() { return steps; }

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
    public Optional<AthenzService> athenzService() { return athenzService; }

    /** Cloud account set on the deployment root; see discussion for {@link #athenzService}. */
    public Optional<CloudAccount> cloudAccount() { return cloudAccount; }

    /**
     * Returns the most specific zone endpoint, where specificity is given, in decreasing order:
     * 1. The given instance has declared a zone endpoint for the cluster, for the given region.
     * 2. The given instance has declared a universal zone endpoint for the cluster.
     * 3. The application has declared a zone endpoint for the cluster, for the given region.
     * 4. The application has declared a universal zone endpoint for the cluster.
     * 5. None of the above apply, and the default of a publicly visible endpoint is used.
     */
    public ZoneEndpoint zoneEndpoint(InstanceName instance, ZoneId zone, ClusterSpec.Id cluster) {
        // TODO: look up endpoints from <dev> tag, or so, if we're to support non-prod settings.
        if (   zone.environment().isTest()
            && instances().stream()
                          .anyMatch(spec -> spec.zoneEndpoints().getOrDefault(cluster, Map.of()).values().stream()
                                                .anyMatch(endpoint -> ! endpoint.isPublicEndpoint()))) return ZoneEndpoint.privateEndpoint;
        if (zone.environment() != Environment.prod) return ZoneEndpoint.defaultEndpoint;
        return instance(instance).flatMap(spec -> spec.zoneEndpoint(zone, cluster))
                                 .orElse(ZoneEndpoint.defaultEndpoint);
    }

    /** @deprecated returns Bcp.empty(). */
    @Deprecated // Remove after June 2023
    public Bcp bcp() { return Bcp.empty(); }

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
            throw new IllegalArgumentException("No instance '" + name + "' in deployment.xml. Instances: " +
                                               instances().stream().map(spec -> spec.name().toString()).collect(Collectors.joining(",")));
        return instance.get();
    }

    /** Returns the instance names declared in this */
    public List<InstanceName> instanceNames() {
        return instances().stream().map(DeploymentInstanceSpec::name).toList();
    }

    /** Returns the step descendants of this which are instances */
    public List<DeploymentInstanceSpec> instances() {
        return instances(steps);
    }

    /** Returns the application-level endpoints of this, if any */
    public List<Endpoint> endpoints() {
        return endpoints;
    }

    /** Returns the deprecated elements used when creating this */
    public List<DeprecatedElement> deprecatedElements() {
        return deprecatedElements;
    }

    private static List<DeploymentInstanceSpec> instances(List<DeploymentSpec.Step> steps) {
        return steps.stream()
                    .flatMap(DeploymentSpec::flatten)
                    .toList();
    }

    private static Stream<DeploymentInstanceSpec> flatten(Step step) {
        if (step instanceof DeploymentInstanceSpec) return Stream.of((DeploymentInstanceSpec) step);
        return step.steps().stream().flatMap(DeploymentSpec::flatten);
    }


    private static void illegal(String message) {
        throw new IllegalArgumentException(message);
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

    /** Computes a hash of all fields that influence what is deployed with this spec, i.e., not orchestration. */
    public int deployableHashCode() {
        Object[] toHash = new Object[instances().size() + 4];
        int i = 0;
        toHash[i++] = majorVersion;
        toHash[i++] = athenzDomain;
        toHash[i++] = athenzService;
        toHash[i++] = endpoints;
        for (DeploymentInstanceSpec instance : instances())
            toHash[i++] = instance.deployableHashCode();

        return Arrays.hashCode(toHash);
    }

    /** A deployment step */
    public abstract static class Step {

        /** Returns whether this step specifies the given environment. */
        public final boolean concerns(Environment environment) {
            return concerns(environment, Optional.empty());
        }

        /**
         * Returns whether this step specifies the given environment, and, optionally,
         * if this step specifies a region, whether this is also the given region.
         */
        public abstract boolean concerns(Environment environment, Optional<RegionName> region);

        /** Returns the zones deployed to in this step. */
        public List<DeclaredZone> zones() { return Collections.emptyList(); }

        /** The delay introduced by this step (beyond the time it takes to execute the step). */
        public Duration delay() { return Duration.ZERO; }

        /** Returns any steps nested in this. */
        public List<Step> steps() { return List.of(); }

        /** Returns whether this step is a test step. */
        public boolean isTest() { return false; }

        /** Returns whether the nested steps in this, if any, should be performed in declaration order. */
        public boolean isOrdered() {
            return true;
        }

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
        public boolean concerns(Environment environment, Optional<RegionName> region) { return false; }

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
        private final Optional<CloudAccount> cloudAccount;

        public DeclaredZone(Environment environment) {
            this(environment, Optional.empty(), false, Optional.empty(), Optional.empty(), Optional.empty());
        }

        public DeclaredZone(Environment environment, Optional<RegionName> region, boolean active,
                            Optional<AthenzService> athenzService, Optional<String> testerFlavor,
                            Optional<CloudAccount> cloudAccount) {
            if (environment != Environment.prod && region.isPresent())
                illegal("Non-prod environments cannot specify a region");
            if (environment == Environment.prod && region.isEmpty())
                illegal("Prod environments must be specified with a region");
            this.environment = Objects.requireNonNull(environment);
            this.region = Objects.requireNonNull(region);
            this.active = active;
            this.athenzService = Objects.requireNonNull(athenzService);
            this.testerFlavor = Objects.requireNonNull(testerFlavor);
            this.cloudAccount = Objects.requireNonNull(cloudAccount);
        }

        public Environment environment() { return environment; }

        /** The region name, or empty if not declared */
        public Optional<RegionName> region() { return region; }

        /** Returns whether this zone should receive production traffic */
        public boolean active() { return active; }

        public Optional<String> testerFlavor() { return testerFlavor; }

        public Optional<AthenzService> athenzService() { return athenzService; }

        public Optional<CloudAccount> cloudAccount() {
            return cloudAccount;
        }

        @Override
        public List<DeclaredZone> zones() { return List.of(this); }

        @Override
        public boolean concerns(Environment environment, Optional<RegionName> region) {
            if (environment != this.environment) return false;
            if (region.isPresent() && this.region.isPresent() && ! region.equals(this.region)) return false;
            return true;
        }

        @Override
        public boolean isTest() { return environment.isTest(); }

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

    /** A declared production test */
    public static class DeclaredTest extends Step {

        private final RegionName region;

        public DeclaredTest(RegionName region) {
            this.region = Objects.requireNonNull(region);
        }

        @Override
        public boolean concerns(Environment environment, Optional<RegionName> region) {
            return region.map(this.region::equals).orElse(true) && environment == Environment.prod;
        }

        @Override
        public boolean isTest() { return true; }

        /** Returns the region this test is for. */
        public RegionName region() {
            return region;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DeclaredTest that = (DeclaredTest) o;
            return region.equals(that.region);
        }

        @Override
        public int hashCode() {
            return Objects.hash(region);
        }

        @Override
        public String toString() {
            return "tests for prod." + region;
        }

    }

    /** A container for several steps, by default in serial order */
    public static class Steps extends Step {

        private final List<Step> steps;

        public Steps(List<Step> steps) {
            this.steps = List.copyOf(steps);
        }

        @Override
        public List<DeclaredZone> zones() {
            return steps.stream()
                        .flatMap(step -> step.zones().stream())
                        .toList();
        }

        @Override
        public List<Step> steps() { return steps; }

        @Override
        public boolean concerns(Environment environment, Optional<RegionName> region) {
            return steps.stream().anyMatch(step -> step.concerns(environment, region));
        }

        @Override
        public Duration delay() {
            return steps.stream().map(Step::delay).reduce(Duration.ZERO, Duration::plus);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return steps.equals(((Steps) o).steps);
        }

        @Override
        public int hashCode() {
            return Objects.hash(steps);
        }

        @Override
        public String toString() {
            return steps.size() + " steps";
        }

    }

    /** A container for multiple other steps, which are executed in parallel */
    public static class ParallelSteps extends Steps {

        public ParallelSteps(List<Step> steps) {
            super(steps);
        }

        @Override
        public Duration delay() {
            return steps().stream().map(Step::delay).max(Comparator.naturalOrder()).orElse(Duration.ZERO);
        }

        @Override
        public boolean isOrdered() {
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if ( ! (o instanceof ParallelSteps)) return false;
            return Objects.equals(steps(), ((ParallelSteps) o).steps());
        }

        @Override
        public int hashCode() {
            return Objects.hash(steps());
        }

        @Override
        public String toString() {
            return steps().size() + " parallel steps";
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


    /** Determines what application changes to deploy to the instance. */
    public enum RevisionTarget {
        /** Next: Application changes are rolled through this instance in the same manner as they become ready, optionally adjusted further by min and max risk settings. */
        next,
        /** Latest: Application changes are always merged, so the latest available is always chosen for roll-out. */
        latest
    }


    /** Determines when application changes deploy. */
    public enum RevisionChange {
        /** Exclusive: Application changes always wait for already rolling application changes to complete. */
        whenClear,
        /** Separate: Application changes wait for already rolling application changes to complete, unless they fail. */
        whenFailing,
        /** Latest: Application changes immediately supersede previous application changes, unless currently blocked. */
        always
    }


    /** Determines when application changes deploy, when there is already an ongoing platform upgrade. */
    public enum UpgradeRollout {
        /** Separate: Application changes wait for upgrade to complete, unless upgrade fails. */
        separate,
        /** Leading: Application changes are allowed to start and catch up to the platform upgrade. */
        leading,
        // /** Simultaneous: Application changes deploy independently of platform upgrades. */
        simultaneous
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

    /**
     * Represents a deprecated XML element in {@link com.yahoo.config.application.api.DeploymentSpec}, or the deprecated
     * attribute(s) of an element.
     */
    public static class DeprecatedElement {

        private final String tagName;
        private final List<String> attributes;
        private final String message;
        private final int majorVersion;

        public DeprecatedElement(int majorVersion, String tagName, List<String> attributes, String message) {
            this.tagName = Objects.requireNonNull(tagName);
            this.attributes = Objects.requireNonNull(attributes);
            this.message = Objects.requireNonNull(message);
            this.majorVersion = majorVersion;
            if (message.isBlank()) throw new IllegalArgumentException("message must be non-empty");
        }

        /** Returns the major version that deprecated this element */
        public int majorVersion() {
            return majorVersion;
        }

        public String humanReadableString() {
            String deprecationDescription = "deprecated since major version " + majorVersion;
            if (attributes.isEmpty()) {
                return "Element '" + tagName + "' is " + deprecationDescription + ". " + message;
            }
            return "Element '" + tagName + "' contains attribute" + (attributes.size() > 1 ? "s " : " ") +
                   attributes.stream().map(attr -> "'" + attr + "'").collect(Collectors.joining(", ")) +
                   " " + deprecationDescription + ". " + message;
        }

        @Override
        public String toString() {
            return humanReadableString();
        }

    }

}
