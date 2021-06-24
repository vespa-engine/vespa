// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.concurrent.maintenance.JobControl;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.hosted.controller.api.integration.ServiceRegistry;
import com.yahoo.vespa.hosted.controller.api.integration.maven.MavenRepository;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.archive.CuratorArchiveBucketDb;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLogger;
import com.yahoo.vespa.hosted.controller.config.ControllerConfig;
import com.yahoo.vespa.hosted.controller.deployment.JobController;
import com.yahoo.vespa.hosted.controller.dns.NameServiceForwarder;
import com.yahoo.vespa.hosted.controller.notification.NotificationsDb;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.persistence.JobControlFlags;
import com.yahoo.vespa.hosted.controller.security.AccessControl;
import com.yahoo.vespa.hosted.controller.support.access.SupportAccessControl;
import com.yahoo.vespa.hosted.controller.versions.ControllerVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersionStatus;
import com.yahoo.vespa.hosted.controller.versions.OsVersionTarget;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
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
public class Controller extends AbstractComponent {

    private static final Logger log = Logger.getLogger(Controller.class.getName());

    private final Supplier<String> hostnameSupplier;
    private final CuratorDb curator;
    private final JobControl jobControl;
    private final ApplicationController applicationController;
    private final TenantController tenantController;
    private final JobController jobController;
    private final Clock clock;
    private final ZoneRegistry zoneRegistry;
    private final ServiceRegistry serviceRegistry;
    private final AuditLogger auditLogger;
    private final FlagSource flagSource;
    private final NameServiceForwarder nameServiceForwarder;
    private final MavenRepository mavenRepository;
    private final Metric metric;
    private final RoutingController routingController;
    private final ControllerConfig controllerConfig;
    private final SecretStore secretStore;
    private final CuratorArchiveBucketDb archiveBucketDb;
    private final NotificationsDb notificationsDb;
    private final SupportAccessControl supportAccessControl;

    /**
     * Creates a controller 
     * 
     * @param curator the curator instance storing the persistent state of the controller.
     */
    @Inject
    public Controller(CuratorDb curator, RotationsConfig rotationsConfig, AccessControl accessControl, FlagSource flagSource,
                      MavenRepository mavenRepository, ServiceRegistry serviceRegistry, Metric metric, SecretStore secretStore,
                      ControllerConfig controllerConfig) {
        this(curator, rotationsConfig, accessControl, com.yahoo.net.HostName::getLocalhost, flagSource,
             mavenRepository, serviceRegistry, metric, secretStore, controllerConfig);
    }

    public Controller(CuratorDb curator, RotationsConfig rotationsConfig, AccessControl accessControl,
                      Supplier<String> hostnameSupplier, FlagSource flagSource, MavenRepository mavenRepository,
                      ServiceRegistry serviceRegistry, Metric metric, SecretStore secretStore, ControllerConfig controllerConfig) {

        this.hostnameSupplier = Objects.requireNonNull(hostnameSupplier, "HostnameSupplier cannot be null");
        this.curator = Objects.requireNonNull(curator, "Curator cannot be null");
        this.serviceRegistry = Objects.requireNonNull(serviceRegistry, "ServiceRegistry cannot be null");
        this.zoneRegistry = Objects.requireNonNull(serviceRegistry.zoneRegistry(), "ZoneRegistry cannot be null");
        this.clock = Objects.requireNonNull(serviceRegistry.clock(), "Clock cannot be null");
        this.flagSource = Objects.requireNonNull(flagSource, "FlagSource cannot be null");
        this.mavenRepository = Objects.requireNonNull(mavenRepository, "MavenRepository cannot be null");
        this.metric = Objects.requireNonNull(metric, "Metric cannot be null");

        nameServiceForwarder = new NameServiceForwarder(curator);
        jobController = new JobController(this);
        applicationController = new ApplicationController(this, curator, accessControl, clock, flagSource, serviceRegistry.billingController());
        tenantController = new TenantController(this, curator, accessControl, flagSource);
        routingController = new RoutingController(this, Objects.requireNonNull(rotationsConfig, "RotationsConfig cannot be null"));
        auditLogger = new AuditLogger(curator, clock);
        jobControl = new JobControl(new JobControlFlags(curator, flagSource));
        archiveBucketDb = new CuratorArchiveBucketDb(this);
        notificationsDb = new NotificationsDb(this);
        this.controllerConfig = controllerConfig;
        this.secretStore = secretStore;
        this.supportAccessControl = new SupportAccessControl(this);

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

    public ControllerConfig controllerConfig() { return controllerConfig; }

    public ApplicationView getApplicationView(String tenantName, String applicationName, String instanceName,
                                              String environment, String region) {
        return serviceRegistry.configServer().getApplicationView(tenantName, applicationName, instanceName, environment, region);
    }

    /** Replace the current version status by a new one */
    public void updateVersionStatus(VersionStatus newStatus) {
        VersionStatus currentStatus = readVersionStatus();
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
    public VersionStatus readVersionStatus() { return curator.readVersionStatus(); }

    /** Remove confidence override for versions matching given filter */
    public void removeConfidenceOverride(Predicate<Version> filter) {
        try (Lock lock = curator.lockConfidenceOverrides()) {
            Map<Version, VespaVersion.Confidence> overrides = new LinkedHashMap<>(curator.readConfidenceOverrides());
            overrides.keySet().removeIf(filter);
            curator.writeConfidenceOverrides(overrides);
        }
    }
    
    /** Returns the current system version: The controller should drive towards running all applications on this version */
    public Version readSystemVersion() {
        return systemVersion(readVersionStatus());
    }

    /** Returns the current system version from given status: The controller should drive towards running all applications on this version */
    public Version systemVersion(VersionStatus versionStatus) {
        return versionStatus.systemVersion()
                            .map(VespaVersion::versionNumber)
                            .orElse(Vtag.currentVersion);
    }

    /** Returns the target OS version for infrastructure in this system. The controller will drive infrastructure OS
     * upgrades to this version */
    public Optional<OsVersionTarget> osVersionTarget(CloudName cloud) {
        return osVersionTargets().stream().filter(target -> target.osVersion().cloud().equals(cloud)).findFirst();
    }

    /** Returns all target OS versions in this system */
    public Set<OsVersionTarget> osVersionTargets() {
        return curator.readOsVersionTargets();
    }

    /** Set the target OS version for given cloud in this system */
    public void upgradeOsIn(CloudName cloudName, Version version, Duration upgradeBudget, boolean force) {
        if (version.isEmpty()) {
            throw new IllegalArgumentException("Invalid version '" + version.toFullString() + "'");
        }
        if (!clouds().contains(cloudName)) {
            throw new IllegalArgumentException("Cloud '" + cloudName + "' does not exist in this system");
        }
        Instant scheduledAt = clock.instant();
        try (Lock lock = curator.lockOsVersions()) {
            Map<CloudName, OsVersionTarget> targets = curator.readOsVersionTargets().stream()
                                                             .collect(Collectors.toMap(t -> t.osVersion().cloud(),
                                                                                       Function.identity()));

            OsVersionTarget currentTarget = targets.get(cloudName);
            if (!force && currentTarget != null) {
                if (currentTarget.osVersion().version().isAfter(version)) {
                    throw new IllegalArgumentException("Cannot downgrade cloud '" + cloudName.value() + "' to version " +
                                                       version.toFullString());
                }
                if (currentTarget.osVersion().version().equals(version) &&
                    currentTarget.upgradeBudget().equals(upgradeBudget)) return; // Version and budget unchanged
            }

            OsVersionTarget newTarget = new OsVersionTarget(new OsVersion(version, cloudName), upgradeBudget, scheduledAt);
            targets.put(cloudName, newTarget);
            curator.writeOsVersionTargets(new TreeSet<>(targets.values()));
            log.info("Triggered OS upgrade to " + version.toFullString() + " in cloud " +
                     cloudName.value() + ", with upgrade budget " + upgradeBudget);
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
                             newVersions.iterator().next().toFullString());
                }
            }
            curator.writeOsVersionStatus(newStatus);
        }
    }

    /** Returns the hostname of this controller */
    public HostName hostname() {
        return HostName.from(hostnameSupplier.get());
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

    public SecretStore secretStore() {
        return secretStore;
    }

    /** Clouds present in this system */
    public Set<CloudName> clouds() {
        return zoneRegistry.zones().all().zones().stream()
                           .map(ZoneApi::getCloudName)
                           .collect(Collectors.toUnmodifiableSet());
    }

    private static String printableVersion(Optional<VespaVersion> vespaVersion) {
        return vespaVersion.map(v -> v.versionNumber().toFullString()).orElse("unknown");
    }

    public JobControl jobControl() {
        return jobControl;
    }

    public CuratorArchiveBucketDb archiveBucketDb() {
        return archiveBucketDb;
    }

    public NotificationsDb notificationsDb() {
        return notificationsDb;
    }

    public SupportAccessControl supportAccess() {
        return supportAccessControl;
    }

}
