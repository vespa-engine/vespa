// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.yahoo.collections.Comparables;
import com.yahoo.config.application.api.xml.DeploymentSpecXmlReader;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Tags;
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
import java.util.StringJoiner;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

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
public final class DeploymentSpec {

    /** The empty deployment spec, specifying no zones or rotation, and defaults for all settings */
    public static final DeploymentSpec empty = new DeploymentSpec(List.of(),
                                                                  Optional.empty(),
                                                                  Optional.empty(),
                                                                  Optional.empty(),
                                                                  Map.of(),
                                                                  Optional.empty(),
                                                                  List.of(),
                                                                  "<deployment version='1.0'/>",
                                                                  List.of(),
                                                                  DevSpec.empty);

    private final List<Step> steps;

    // Attributes which can be set on the root tag and which must be available outside any particular instance
    private final Optional<Integer> majorVersion;
    private final Optional<AthenzDomain> athenzDomain;
    private final Optional<AthenzService> athenzService;
    private final Map<CloudName, CloudAccount> cloudAccounts;
    private final Optional<Duration> hostTTL;
    private final List<Endpoint> endpoints;
    private final List<DeprecatedElement> deprecatedElements;
    private final DevSpec devSpec;

    private final String xmlForm;

    public DeploymentSpec(List<Step> steps,
                          Optional<Integer> majorVersion,
                          Optional<AthenzDomain> athenzDomain,
                          Optional<AthenzService> athenzService,
                          Map<CloudName, CloudAccount> cloudAccounts,
                          Optional<Duration> hostTTL,
                          List<Endpoint> endpoints,
                          String xmlForm,
                          List<DeprecatedElement> deprecatedElements,
                          DevSpec devSpec) {
        this.steps = List.copyOf(Objects.requireNonNull(steps));
        this.majorVersion = Objects.requireNonNull(majorVersion);
        this.athenzDomain = Objects.requireNonNull(athenzDomain);
        this.athenzService = Objects.requireNonNull(athenzService);
        this.cloudAccounts = Map.copyOf(cloudAccounts);
        this.hostTTL = Objects.requireNonNull(hostTTL);
        this.xmlForm = Objects.requireNonNull(xmlForm);
        this.endpoints = List.copyOf(Objects.requireNonNull(endpoints));
        this.deprecatedElements = List.copyOf(Objects.requireNonNull(deprecatedElements));
        this.devSpec = Objects.requireNonNull(devSpec);
        validateTotalDelay(steps);
        validateUpgradePoliciesOfIncreasingConservativeness(steps);
        validateAthenz();
        validateApplicationEndpoints();
        hostTTL.filter(Duration::isNegative).ifPresent(ttl -> illegal("Host TTL cannot be negative"));
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

    /** The most specific Athenz service for the given arguments. */
    public Optional<AthenzService> athenzService(InstanceName instance, Environment environment, RegionName region) {
        return (environment.isManuallyDeployed() ? devSpec.athenzService
                                                 : instance(instance).flatMap(spec -> spec.athenzService(environment, region)))
                .or(this::athenzService);
    }

    /** The most specific Cloud account for the given arguments. */
    public CloudAccount cloudAccount(CloudName cloud, InstanceName instance, ZoneId zone) {
        return (zone.environment().isManuallyDeployed() ? devSpec.cloudAccounts
                                                        : instance(instance).map(spec -> spec.cloudAccounts(zone.environment(), zone.region())))
                .orElse(cloudAccounts)
                .getOrDefault(cloud, CloudAccount.empty);
    }

    public Map<CloudName, CloudAccount> cloudAccounts() { return cloudAccounts; }

    public Tags tags(InstanceName instance, Environment environment) {
        return environment.isManuallyDeployed() ? devSpec.tags
                                                : instance(instance).map(DeploymentInstanceSpec::tags).orElse(Tags.empty());
    }

    /**
     * Additional host time-to-live for this application. Requires a custom cloud account to be set.
     * This also applies only to zones with dynamic provisioning, and is then the time hosts are
     * allowed remain empty, before being deprovisioned. This is useful for applications which frequently
     * deploy to, e.g., test and staging zones, and want to avoid the delay of having to provision hosts.
     */
    public Optional<Duration> hostTTL(InstanceName instance, Environment environment, RegionName region) {
        return (environment.isManuallyDeployed() ? devSpec.hostTTL
                                                 : instance(instance).flatMap(spec -> spec.hostTTL(environment, Optional.of(region))))
                .or(this::hostTTL);
    }

    Optional<Duration> hostTTL() { return hostTTL; }

    // TODO: Used by models up to 8.502
    public ZoneEndpoint zoneEndpoint(InstanceName instance, ZoneId zone, ClusterSpec.Id cluster) {
        return zoneEndpoint(instance, zone, cluster, false);
    }

    /**
     * Returns the most specific zone endpoint, where specificity is given, in decreasing order:
     * 1. The given instance has declared a zone endpoint for the cluster, for the given region.
     * 2. The given instance has declared a universal zone endpoint for the cluster.
     * 3. The application has declared a zone endpoint for the cluster, for the given region.
     * 4. The application has declared a universal zone endpoint for the cluster.
     * 5. None of the above apply, and the default of a publicly visible endpoint is used.
     */
    // TODO: Available from 8.502. Used to roll out useNonPublicEndpointForTest.
    public ZoneEndpoint zoneEndpoint(InstanceName instance, ZoneId zone, ClusterSpec.Id cluster, boolean useNonPublicEndpointForTest) {
        if (zone.environment().isTest() &&
            (useNonPublicEndpointForTest ||
             instances().stream()
                        .anyMatch(spec -> spec.zoneEndpoints()
                                              .getOrDefault(cluster, Map.of())
                                              .values()
                                              .stream()
                                              .anyMatch(endpoint -> ! endpoint.isPublicEndpoint()))) &&
            // Remove once Azure supports private endpoints
            !zone.region().value().startsWith(CloudName.AZURE.value() + "-")) {

            return ZoneEndpoint.privateEndpoint;
        }

        if (zone.environment().isManuallyDeployed())
            return devSpec.zoneEndpoints.getOrDefault(cluster, ZoneEndpoint.defaultEndpoint);

        return instance(instance).flatMap(spec -> spec.zoneEndpoint(zone, cluster))
                                 .orElse(ZoneEndpoint.defaultEndpoint);
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
            throw new IllegalArgumentException("No instance '" + name + "' in deployment.xml. Instances: " +
                                               instances().stream().map(spec -> spec.name().toString()).collect(joining(",")));
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


    static void illegal(String message) {
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
            if (!b.isEmpty()) {
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
        Object[] toHash = new Object[instances().size() + 5];
        int i = 0;
        toHash[i++] = majorVersion;
        toHash[i++] = athenzDomain;
        toHash[i++] = athenzService;
        toHash[i++] = endpoints;
        toHash[i++] = cloudAccounts;
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
        public List<DeclaredZone> zones() { return List.of(); }

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

        public Optional<Duration> hostTTL() { return Optional.empty(); }

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
        private final Optional<AthenzService> athenzService;
        private final Optional<String> testerNodes;
        private final Map<CloudName, CloudAccount> cloudAccounts;
        private final Optional<Duration> hostTTL;

        public DeclaredZone(Environment environment) {
            this(environment, Optional.empty(), Optional.empty(), Optional.empty(), Map.of(), Optional.empty());
        }

        public DeclaredZone(Environment environment, Optional<RegionName> region, Optional<AthenzService> athenzService,
                            Optional<String> testerNodes, Map<CloudName, CloudAccount> cloudAccounts, Optional<Duration> hostTTL) {
            if (environment != Environment.prod && region.isPresent())
                illegal("Non-prod environments cannot specify a region");
            if (environment == Environment.prod && region.isEmpty())
                illegal("Prod environments must be specified with a region");
            hostTTL.filter(Duration::isNegative).ifPresent(ttl -> illegal("Host TTL cannot be negative"));
            this.environment = Objects.requireNonNull(environment);
            this.region = Objects.requireNonNull(region);
            this.athenzService = Objects.requireNonNull(athenzService);
            this.testerNodes = Objects.requireNonNull(testerNodes);
            this.cloudAccounts = Map.copyOf(cloudAccounts);
            this.hostTTL = Objects.requireNonNull(hostTTL);
        }

        public Environment environment() { return environment; }

        /** The region name, or empty if not declared */
        public Optional<RegionName> region() { return region; }

        /** The XML &lt;nodes&gt; tag of the tester application for this zone, if specified. */
        public Optional<String> testerNodes() { return testerNodes; }

        Optional<AthenzService> athenzService() { return athenzService; }

        Map<CloudName, CloudAccount> cloudAccounts() { return cloudAccounts; }

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

        @Override
        public Optional<Duration> hostTTL() {
            return hostTTL;
        }

    }

    /** A declared production test */
    public static class DeclaredTest extends Step {

        private final RegionName region;
        private final Optional<Duration> hostTTL;

        public DeclaredTest(RegionName region, Optional<Duration> hostTTL) {
            this.region = Objects.requireNonNull(region);
            this.hostTTL = Objects.requireNonNull(hostTTL);
            hostTTL.filter(Duration::isNegative).ifPresent(ttl -> illegal("Host TTL cannot be negative"));
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
        public Optional<Duration> hostTTL() {
            return hostTTL;
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
                   attributes.stream().map(attr -> "'" + attr + "'").collect(joining(", ")) +
                   " " + deprecationDescription + ". " + message;
        }

        @Override
        public String toString() {
            return humanReadableString();
        }

    }

    public static class DevSpec {

        public static final DevSpec empty = new DevSpec(Optional.empty(), Optional.empty(), Optional.empty(), Tags.empty(), Map.of());

        private final Optional<AthenzService> athenzService;
        private final Optional<Map<CloudName, CloudAccount>> cloudAccounts;
        private final Optional<Duration> hostTTL;
        private final Tags tags;
        private final Map<ClusterSpec.Id, ZoneEndpoint> zoneEndpoints;

        public DevSpec(Optional<AthenzService> athenzService,
                       Optional<Map<CloudName, CloudAccount>> cloudAccounts,
                       Optional<Duration> hostTTL,
                       Tags tags,
                       Map<ClusterSpec.Id, ZoneEndpoint> zoneEndpoints) {
            this.athenzService = Objects.requireNonNull(athenzService);
            this.cloudAccounts = cloudAccounts.map(Map::copyOf);
            this.hostTTL = Objects.requireNonNull(hostTTL);
            this.tags = Objects.requireNonNull(tags);
            this.zoneEndpoints = Map.copyOf(zoneEndpoints);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DevSpec devSpec = (DevSpec) o;
            return Objects.equals(athenzService, devSpec.athenzService) && Objects.equals(cloudAccounts, devSpec.cloudAccounts) && Objects.equals(hostTTL, devSpec.hostTTL) && Objects.equals(tags, devSpec.tags) && Objects.equals(zoneEndpoints, devSpec.zoneEndpoints);
        }

        @Override
        public int hashCode() {
            return Objects.hash(athenzService, cloudAccounts, hostTTL, tags, zoneEndpoints);
        }

        @Override
        public String toString() {
            StringJoiner joiner = new StringJoiner(", ", "dev settings: ", "").setEmptyValue("no dev settings");
            athenzService.ifPresent(service -> joiner.add("athenz-service: " + service.value()));
            cloudAccounts.ifPresent(cas -> joiner.add(cas.entrySet().stream().map(ca -> ca.getKey() + ": " + ca.getValue()).collect(joining(", ", "cloud accounts: ", ""))));
            hostTTL.ifPresent(ttl -> joiner.add("host-ttl: " + ttl));
            if ( ! tags.isEmpty()) joiner.add("tags: " + tags);
            if ( ! zoneEndpoints.isEmpty()) joiner.add("endpoint settings for clusters: " + zoneEndpoints.keySet().stream().map(ClusterSpec.Id::value).collect(joining(", ")));
            return joiner.toString();
        }

    }

}
