// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.hosted.controller.api.integration.ApplicationIdSnapshot;
import com.yahoo.vespa.hosted.controller.api.integration.ApplicationIdSource;
import com.yahoo.vespa.hosted.controller.api.integration.ServiceRegistry;
import com.yahoo.vespa.hosted.controller.api.integration.maven.MavenRepository;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLogger;
import com.yahoo.vespa.hosted.controller.deployment.JobController;
import com.yahoo.vespa.hosted.controller.dns.NameServiceForwarder;
import com.yahoo.vespa.hosted.controller.metric.ConfigServerMetrics;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.security.AccessControl;
import com.yahoo.vespa.hosted.controller.versions.ControllerVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * API to the controller. This contains the object model of everything the controller cares about, mainly tenants and
 * applications. The object model is persisted to curator.
 * 
 * All the individual model objects reachable from the Controller are immutable.
 * 
 * Access to the controller is multi-thread safe, provided the locking methods are
 * used when accessing, modifying and storing objects provided by the controller.
 * 
 * @author bratseth
 */
public class Controller extends AbstractComponent implements ApplicationIdSource {

    private static final Logger log = Logger.getLogger(Controller.class.getName());

    private final Supplier<String> hostnameSupplier;
    private final CuratorDb curator;
    private final ApplicationController applicationController;
    private final TenantController tenantController;
    private final JobController jobController;
    private final Clock clock;
    private final ZoneRegistry zoneRegistry;
    private final ServiceRegistry serviceRegistry;
    private final ConfigServerMetrics metrics;
    private final AuditLogger auditLogger;
    private final FlagSource flagSource;
    private final NameServiceForwarder nameServiceForwarder;
    private final MavenRepository mavenRepository;
    private final Metric metric;
    private final RoutingController routingController;

    /**
     * Creates a controller 
     * 
     * @param curator the curator instance storing the persistent state of the controller.
     */
    @Inject
    public Controller(CuratorDb curator, RotationsConfig rotationsConfig, AccessControl accessControl, FlagSource flagSource,
                      MavenRepository mavenRepository, ServiceRegistry serviceRegistry, Metric metric, SecretStore secretStore) {
        this(curator, rotationsConfig, accessControl, com.yahoo.net.HostName::getLocalhost, flagSource,
             mavenRepository, serviceRegistry, metric, secretStore);
    }

    public Controller(CuratorDb curator, RotationsConfig rotationsConfig, AccessControl accessControl,
                      Supplier<String> hostnameSupplier, FlagSource flagSource, MavenRepository mavenRepository,
                      ServiceRegistry serviceRegistry, Metric metric, SecretStore secretStore) {

        this.hostnameSupplier = Objects.requireNonNull(hostnameSupplier, "HostnameSupplier cannot be null");
        this.curator = Objects.requireNonNull(curator, "Curator cannot be null");
        this.serviceRegistry = Objects.requireNonNull(serviceRegistry, "ServiceRegistry cannot be null");
        this.zoneRegistry = Objects.requireNonNull(serviceRegistry.zoneRegistry(), "ZoneRegistry cannot be null");
        this.clock = Objects.requireNonNull(serviceRegistry.clock(), "Clock cannot be null");
        this.flagSource = Objects.requireNonNull(flagSource, "FlagSource cannot be null");
        this.mavenRepository = Objects.requireNonNull(mavenRepository, "MavenRepository cannot be null");
        this.metric = Objects.requireNonNull(metric, "Metric cannot be null");

        metrics = new ConfigServerMetrics(serviceRegistry.configServer());
        nameServiceForwarder = new NameServiceForwarder(curator);
        jobController = new JobController(this);
        applicationController = new ApplicationController(this, curator, accessControl, clock, secretStore, flagSource);
        tenantController = new TenantController(this, curator, accessControl);
        routingController = new RoutingController(this, Objects.requireNonNull(rotationsConfig, "RotationsConfig cannot be null"));
        auditLogger = new AuditLogger(curator, clock);

        // Record the version of this controller
        curator().writeControllerVersion(this.hostname(), ControllerVersion.CURRENT);

        jobController.updateStorage();
    }
    
    /** Returns the instance controlling tenants */
    public TenantController tenants() { return tenantController; }

    /** Returns the instance controlling applications */
    public ApplicationController applications() { return applicationController; }

    /** Returns the instance controlling deployment jobs. */
    public JobController jobController() { return jobController; }

    /** Returns the instance controlling routing */
    public RoutingController routing() {
        return routingController;
    }

    /** Returns the service registry of this */
    public ServiceRegistry serviceRegistry() {
        return serviceRegistry;
    }

    /** Provides access to the feature flags of this */
    public FlagSource flagSource() {
        return flagSource;
    }

    public Clock clock() { return clock; }

    public ZoneRegistry zoneRegistry() { return zoneRegistry; }

    public NameServiceForwarder nameServiceForwarder() { return nameServiceForwarder; }

    public MavenRepository mavenRepository() { return mavenRepository; }

    public ApplicationView getApplicationView(String tenantName, String applicationName, String instanceName,
                                              String environment, String region) {
        return serviceRegistry.configServer().getApplicationView(tenantName, applicationName, instanceName, environment, region);
    }

    /** Replace the current version status by a new one */
    public void updateVersionStatus(VersionStatus newStatus) {
        VersionStatus currentStatus = versionStatus();
        if (newStatus.systemVersion().isPresent() &&
            ! newStatus.systemVersion().equals(currentStatus.systemVersion())) {
            log.info("Changing system version from " + printableVersion(currentStatus.systemVersion()) +
                     " to " + printableVersion(newStatus.systemVersion()));
        }
        curator.writeVersionStatus(newStatus);
        // Removes confidence overrides for versions that no longer exist in the system
        removeConfidenceOverride(version -> newStatus.versions().stream()
                                                     .noneMatch(vespaVersion -> vespaVersion.versionNumber()
                                                                                            .equals(version)));
    }
    
    /** Returns the latest known version status. Calling this is free but the status may be slightly out of date. */
    public VersionStatus versionStatus() { return curator.readVersionStatus(); }

    /** Remove confidence override for versions matching given filter */
    public void removeConfidenceOverride(Predicate<Version> filter) {
        try (Lock lock = curator.lockConfidenceOverrides()) {
            Map<Version, VespaVersion.Confidence> overrides = new LinkedHashMap<>(curator.readConfidenceOverrides());
            overrides.keySet().removeIf(filter);
            curator.writeConfidenceOverrides(overrides);
        }
    }
    
    /** Returns the current system version: The controller should drive towards running all applications on this version */
    public Version systemVersion() {
        return versionStatus().systemVersion()
                              .map(VespaVersion::versionNumber)
                              .orElse(Vtag.currentVersion);
    }

    /** Returns the target OS version for infrastructure in this system. The controller will drive infrastructure OS
     * upgrades to this version */
    public Optional<OsVersion> osVersion(CloudName cloud) {
        return osVersions().stream().filter(osVersion -> osVersion.cloud().equals(cloud)).findFirst();
    }

    /** Returns all target OS versions in this system */
    public Set<OsVersion> osVersions() {
        return curator.readOsVersions();
    }

    /** Set the target OS version for infrastructure on cloud in this system */
    public void upgradeOsIn(CloudName cloud, Version version, boolean force) {
        if (version.isEmpty()) {
            throw new IllegalArgumentException("Invalid version '" + version.toFullString() + "'");
        }
        if (!clouds().contains(cloud)) {
            throw new IllegalArgumentException("Cloud '" + cloud.value() + "' does not exist in this system");
        }
        try (Lock lock = curator.lockOsVersions()) {
            Set<OsVersion> versions = new TreeSet<>(curator.readOsVersions());
            if (!force && versions.stream().anyMatch(osVersion -> osVersion.cloud().equals(cloud) &&
                                                                  osVersion.version().isAfter(version))) {
                throw new IllegalArgumentException("Cannot downgrade cloud '" + cloud.value() + "' to version " +
                                                   version.toFullString());
            }
            versions.removeIf(osVersion -> osVersion.cloud().equals(cloud)); // Only allow a single target per cloud
            versions.add(new OsVersion(version, cloud));
            curator.writeOsVersions(versions);
        }
    }

    /** Returns the current OS version status */
    public OsVersionStatus osVersionStatus() {
        return curator.readOsVersionStatus();
    }

    /** Replace the current OS version status with a new one */
    public void updateOsVersionStatus(OsVersionStatus newStatus) {
        try (Lock lock = curator.lockOsVersionStatus()) {
            OsVersionStatus currentStatus = curator.readOsVersionStatus();
            for (CloudName cloud : clouds()) {
                Set<Version> newVersions = newStatus.versionsIn(cloud);
                if (currentStatus.versionsIn(cloud).size() > 1 && newVersions.size() == 1) {
                    log.info("All nodes in " + cloud + " cloud upgraded to OS version " +
                             newVersions.iterator().next());
                }
            }
            curator.writeOsVersionStatus(newStatus);
        }
    }

    /** Returns the hostname of this controller */
    public HostName hostname() {
        return HostName.from(hostnameSupplier.get());
    }

    public ConfigServerMetrics metrics() {
        return metrics;
    }

    public SystemName system() {
        return zoneRegistry.system();
    }

    public CuratorDb curator() {
        return curator;
    }

    public AuditLogger auditLogger() {
        return auditLogger;
    }

    public Metric metric() {
        return metric;
    }

    private Set<CloudName> clouds() {
        return zoneRegistry.zones().all().zones().stream()
                           .map(ZoneApi::getCloudName)
                           .collect(Collectors.toUnmodifiableSet());
    }

    private static String printableVersion(Optional<VespaVersion> vespaVersion) {
        return vespaVersion.map(v -> v.versionNumber().toFullString()).orElse("unknown");
    }

    @Override
    public ApplicationIdSnapshot applicationIdSnapshot() {
        ApplicationIdSnapshot.Builder snapshotBuilder = new ApplicationIdSnapshot.Builder();
        tenants().asList().forEach(tenant -> snapshotBuilder.add(tenant.name()));
        applications().asList().forEach(application -> {
            snapshotBuilder.add(application.id().tenant(), application.id().application());
            application.instances().values().stream().map(Instance::id).forEach(snapshotBuilder::add);
        });

        return snapshotBuilder.build();
    }
}
