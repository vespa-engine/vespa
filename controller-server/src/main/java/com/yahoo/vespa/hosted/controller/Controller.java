// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.maintenance.JobControl;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.jdisc.Metric;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.hosted.controller.api.integration.ServiceRegistry;
import com.yahoo.vespa.hosted.controller.api.integration.maven.MavenRepository;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.application.MailVerifier;
import com.yahoo.vespa.hosted.controller.archive.CuratorArchiveBucketDb;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLogger;
import com.yahoo.vespa.hosted.controller.config.ControllerConfig;
import com.yahoo.vespa.hosted.controller.deployment.JobController;
import com.yahoo.vespa.hosted.controller.dns.NameServiceForwarder;
import com.yahoo.vespa.hosted.controller.notification.NotificationsDb;
import com.yahoo.vespa.hosted.controller.notification.Notifier;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.persistence.JobControlFlags;
import com.yahoo.vespa.hosted.controller.restapi.dataplanetoken.DataplaneTokenService;
import com.yahoo.vespa.hosted.controller.security.AccessControl;
import com.yahoo.vespa.hosted.controller.support.access.SupportAccessControl;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;
import com.yahoo.yolean.concurrent.Sleeper;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;

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

    private final CuratorDb curator;
    private final JobControl jobControl;
    private final ApplicationController applicationController;
    private final TenantController tenantController;
    private final JobController jobController;
    private final Clock clock;
    private final Sleeper sleeper;
    private final ZoneRegistry zoneRegistry;
    private final ServiceRegistry serviceRegistry;
    private final AuditLogger auditLogger;
    private final FlagSource flagSource;
    private final NameServiceForwarder nameServiceForwarder;
    private final MavenRepository mavenRepository;
    private final Metric metric;
    private final RoutingController routingController;
    private final OsController osController;
    private final ControllerConfig controllerConfig;
    private final SecretStore secretStore;
    private final CuratorArchiveBucketDb archiveBucketDb;
    private final NotificationsDb notificationsDb;
    private final SupportAccessControl supportAccessControl;
    private final Notifier notifier;
    private final MailVerifier mailVerifier;
    private final DataplaneTokenService dataplaneTokenService;
    private final Random random;
    private final Random secureRandom; // Type is Random to allow for test determinism

    /**
     * Creates a controller 
     * 
     * @param curator the curator instance storing the persistent state of the controller.
     */
    @Inject
    public Controller(CuratorDb curator, RotationsConfig rotationsConfig, AccessControl accessControl, FlagSource flagSource,
                      MavenRepository mavenRepository, ServiceRegistry serviceRegistry, Metric metric, SecretStore secretStore,
                      ControllerConfig controllerConfig) {
        this(curator, rotationsConfig, accessControl, flagSource,
             mavenRepository, serviceRegistry, metric, secretStore, controllerConfig, Sleeper.DEFAULT, new Random(),
             new SecureRandom());
    }

    public Controller(CuratorDb curator, RotationsConfig rotationsConfig, AccessControl accessControl,
                      FlagSource flagSource, MavenRepository mavenRepository,
                      ServiceRegistry serviceRegistry, Metric metric, SecretStore secretStore,
                      ControllerConfig controllerConfig, Sleeper sleeper, Random random, Random secureRandom) {
        this.curator = Objects.requireNonNull(curator, "Curator cannot be null");
        this.serviceRegistry = Objects.requireNonNull(serviceRegistry, "ServiceRegistry cannot be null");
        this.zoneRegistry = Objects.requireNonNull(serviceRegistry.zoneRegistry(), "ZoneRegistry cannot be null");
        this.clock = Objects.requireNonNull(serviceRegistry.clock(), "Clock cannot be null");
        this.sleeper = Objects.requireNonNull(sleeper, "Sleeper cannot be null");
        this.flagSource = Objects.requireNonNull(flagSource, "FlagSource cannot be null");
        this.mavenRepository = Objects.requireNonNull(mavenRepository, "MavenRepository cannot be null");
        this.metric = Objects.requireNonNull(metric, "Metric cannot be null");
        this.controllerConfig = Objects.requireNonNull(controllerConfig, "ControllerConfig cannot be null");
        this.secretStore = Objects.requireNonNull(secretStore, "SecretStore cannot be null");
        this.random = Objects.requireNonNull(random, "Random cannot be null");
        this.secureRandom = Objects.requireNonNull(secureRandom, "SecureRandom cannot be null");

        nameServiceForwarder = new NameServiceForwarder(curator);
        jobController = new JobController(this);
        applicationController = new ApplicationController(this, curator, accessControl, clock, flagSource, serviceRegistry.billingController());
        tenantController = new TenantController(this, curator, accessControl);
        routingController = new RoutingController(this, rotationsConfig);
        osController = new OsController(this);
        auditLogger = new AuditLogger(curator, clock);
        jobControl = new JobControl(new JobControlFlags(curator, flagSource));
        archiveBucketDb = new CuratorArchiveBucketDb(this);
        notifier = new Notifier(curator, serviceRegistry.zoneRegistry(), serviceRegistry.mailer(), flagSource);
        notificationsDb = new NotificationsDb(this);
        supportAccessControl = new SupportAccessControl(this);
        mailVerifier = new MailVerifier(serviceRegistry.zoneRegistry().dashboardUrl(), tenantController, serviceRegistry.mailer(), curator, clock);
        dataplaneTokenService = new DataplaneTokenService(this);

        // Record the version of this controller
        curator().writeControllerVersion(this.hostname(), serviceRegistry.controllerVersion());

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

    /** Returns the instance controlling OS upgrades */
    public OsController os() {
        return osController;
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

    public Sleeper sleeper() { return sleeper; }

    public ZoneRegistry zoneRegistry() { return zoneRegistry; }

    public NameServiceForwarder nameServiceForwarder() { return nameServiceForwarder; }

    public MavenRepository mavenRepository() { return mavenRepository; }

    public ControllerConfig controllerConfig() { return controllerConfig; }

    /** Replace the current version status by a new one */
    public void updateVersionStatus(VersionStatus newStatus) {
        VersionStatus currentStatus = readVersionStatus();
        if (newStatus.systemVersion().isPresent() &&
            ! newStatus.systemVersion().equals(currentStatus.systemVersion())) {
            log.info("Changing system version from " + printableVersion(currentStatus.systemVersion()) +
                     " to " + printableVersion(newStatus.systemVersion()));
        }
        Set<Version> obsoleteVersions = currentStatus.versions().stream().map(VespaVersion::versionNumber).collect(toSet());
        for (VespaVersion version : newStatus.versions()) {
            obsoleteVersions.remove(version.versionNumber());
            VespaVersion current = currentStatus.version(version.versionNumber());
            if (current == null)
                log.info("New version " + version.versionNumber().toFullString() + " added");
            else if ( ! current.confidence().equals(version.confidence()))
                log.info("Confidence for version " + version.versionNumber().toFullString() +
                         " changed from " + current.confidence() + " to " + version.confidence());
        }
        for (Version version : obsoleteVersions)
            log.info("Version " + version.toFullString() + " is obsolete, and will be forgotten");

        curator.writeVersionStatus(newStatus);
        removeConfidenceOverride(obsoleteVersions::contains);
    }

    /** Returns the latest known version status. Calling this is free but the status may be slightly out of date. */
    public VersionStatus readVersionStatus() { return curator.readVersionStatus(); }

    /** Remove confidence override for versions matching given filter */
    public void removeConfidenceOverride(Predicate<Version> filter) {
        try (Mutex lock = curator.lockConfidenceOverrides()) {
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

    /** Returns the hostname of this controller */
    public HostName hostname() {
        return serviceRegistry.getHostname();
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

    public Notifier notifier() {
        return notifier;
    }

    public MailVerifier mailVerifier() {
        return mailVerifier;
    }

    public DataplaneTokenService dataplaneTokenService() {
        return dataplaneTokenService;
    }

    /** Returns a random number generator. If secure is true, this returns a {@link SecureRandom} suitable for
     * cryptographic purposes */
    public Random random(boolean secure) {
        return secure ? secureRandom : random;
    }

}
