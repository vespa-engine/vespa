// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.modelfactory;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.api.Provisioned;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationLockException;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeAllocationException;
import com.yahoo.config.provision.TransientException;
import com.yahoo.config.provision.Zone;
import com.yahoo.lang.SettableOptional;
import com.yahoo.vespa.config.server.http.InternalServerException;
import com.yahoo.vespa.config.server.http.InvalidApplicationException;
import com.yahoo.vespa.config.server.http.UnknownVespaVersionException;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.provision.ProvisionerAdapter;
import com.yahoo.vespa.config.server.provision.StaticProvisioner;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Responsible for building the right versions of application models for a given tenant and application generation.
 * Actual model building is implemented by subclasses because it differs in the prepare and activate phases.
 *
 * @author bratseth
 */
public abstract class ModelsBuilder<MODELRESULT extends ModelResult> {

    private static final Logger log = Logger.getLogger(ModelsBuilder.class.getName());

    private final ModelFactoryRegistry modelFactoryRegistry;
    protected final ConfigserverConfig configserverConfig;

    /** True if we are running in hosted mode */
    protected final boolean hosted;

    private final Zone zone;

    private final HostProvisionerProvider hostProvisionerProvider;

    private final DeployLogger deployLogger;

    ModelsBuilder(ModelFactoryRegistry modelFactoryRegistry,
                  ConfigserverConfig configserverConfig,
                  Zone zone,
                  HostProvisionerProvider hostProvisionerProvider,
                  DeployLogger deployLogger) {
        this.modelFactoryRegistry = modelFactoryRegistry;
        this.configserverConfig = configserverConfig;
        this.hosted = configserverConfig.hostedVespa();
        this.zone = zone;
        this.hostProvisionerProvider = hostProvisionerProvider;
        this.deployLogger = deployLogger;
    }

    /** Returns the zone this is running in */
    protected Zone zone() { return zone; }

    protected DeployLogger  deployLogger() { return deployLogger; }

    /**
     * Builds all applicable model versions
     * 
     * @param allocatedHosts the newest version (major and minor) (which is loaded first) decides the allocated hosts
     *                       and assigns to this SettableOptional such that it can be used after this method returns
     */
    public List<MODELRESULT> buildModels(ApplicationId applicationId,
                                         Optional<DockerImage> dockerImageRepository,
                                         Version wantedNodeVespaVersion,
                                         ApplicationPackage applicationPackage,
                                         AllocatedHostsFromAllModels allocatedHosts,
                                         Instant now) {
        Instant start = Instant.now();
        log.log(Level.FINE, () -> "Will build models for " + applicationId);
        Set<Version> versions = modelFactoryRegistry.allVersions();

        // If the application specifies a major, skip models on a newer major
        Optional<Integer> requestedMajorVersion = applicationPackage.getMajorVersion();
        if (requestedMajorVersion.isPresent()) {
            versions = keepUpToMajorVersion(requestedMajorVersion.get(), versions);
            if (versions.isEmpty())
                throw new UnknownVespaVersionException("No Vespa versions on or before major version " +
                                                       requestedMajorVersion.get() + " are present");
        }

        // Load models one major version at a time (in reverse order) as new major versions are allowed
        // to be non-loadable in the case where an existing application is incompatible with a new
        // major version (which is possible by the definition of major)
        List<Integer> majorVersions = versions.stream()
                                              .map(Version::getMajor)
                                              .distinct()
                                              .sorted(Comparator.reverseOrder())
                                              .collect(Collectors.toList());

        List<MODELRESULT> allApplicationModels = new ArrayList<>();
        // Build latest model for latest major only, if that fails build latest model for previous major
        boolean buildLatestModelForThisMajor = true;
        for (int i = 0; i < majorVersions.size(); i++) {
            int majorVersion = majorVersions.get(i);
            try {
                allApplicationModels.addAll(buildModelVersions(keepMajorVersion(majorVersion, versions),
                                                               applicationId, dockerImageRepository, wantedNodeVespaVersion,
                                                               applicationPackage, allocatedHosts, now,
                                                               buildLatestModelForThisMajor, majorVersion));
                buildLatestModelForThisMajor = false; // We have successfully built latest model version, do it only for this major
            }
            catch (NodeAllocationException | ApplicationLockException | TransientException e) {
                // Don't wrap this exception, and don't try to load other model versions as this is (most likely)
                // caused by the state of the system, not the model version/application combination
                throw e;
            }
            catch (RuntimeException e) {
                if (shouldSkipCreatingMajorVersionOnError(majorVersions, majorVersion, wantedNodeVespaVersion, allocatedHosts)) {
                    log.log(Level.FINE, applicationId + ": Skipping major version " + majorVersion, e);
                }
                else {
                    if (e instanceof IllegalArgumentException) {
                        var wrapped = new InvalidApplicationException("Error loading " + applicationId, e);
                        deployLogger.logApplicationPackage(Level.SEVERE, Exceptions.toMessageString(wrapped));
                        throw wrapped;
                    }
                    else {
                        log.log(Level.WARNING, "Unexpected error building " + applicationId, e);
                        throw new InternalServerException("Unexpected error building " + applicationId, e);
                    }
                }
            }
        }
        log.log(Level.FINE, () -> "Done building models for " + applicationId + ". Built models for versions " +
                                  allApplicationModels.stream()
                                                      .map(result -> result.getModel().version())
                                                      .map(Version::toFullString)
                                                      .collect(Collectors.toSet()) +
                                  " in " + Duration.between(start, Instant.now()));
        return allApplicationModels;
    }

    private boolean shouldSkipCreatingMajorVersionOnError(List<Integer> majorVersions, Integer majorVersion, Version wantedVersion,
                                                          AllocatedHostsFromAllModels allHosts) {
        if (majorVersion.equals(wantedVersion.getMajor())) return false;        // Ensure we are valid for our targeted major.
        if (allHosts.toAllocatedHosts().getHosts().stream()
                    .flatMap(host -> host.version().stream())
                    .map(Version::getMajor)
                    .anyMatch(majorVersion::equals)) return false;              // Ensure we are valid for our currently deployed major.
        if (majorVersion.equals(Collections.min(majorVersions))) return false;  // Probably won't happen if the other two are both false ... ?
        // Note: This needs to be bumped when we no longer want to support successfully deploying
        // applications that are not working on version 8, but are working on a lower major version (unless
        // apps have explicitly defined major version to deploy to in application package)
        return majorVersion >= 8;
    }

    // versions is the set of versions for one particular major version
    private List<MODELRESULT> buildModelVersions(Set<Version> versions,
                                                 ApplicationId applicationId,
                                                 Optional<DockerImage> wantedDockerImageRepository,
                                                 Version wantedNodeVespaVersion,
                                                 ApplicationPackage applicationPackage,
                                                 AllocatedHostsFromAllModels allocatedHosts,
                                                 Instant now,
                                                 boolean buildLatestModelForThisMajor,
                                                 int majorVersion) {
        List<MODELRESULT> builtModelVersions = new ArrayList<>();
        Optional<Version> latest = Optional.empty();
        if (buildLatestModelForThisMajor) {
            latest = Optional.of(findLatest(versions));
            // load latest application version
            MODELRESULT latestModelVersion = buildModelVersion(modelFactoryRegistry.getFactory(latest.get()),
                                                               applicationPackage,
                                                               applicationId,
                                                               wantedDockerImageRepository,
                                                               wantedNodeVespaVersion);
            allocatedHosts.add(latestModelVersion.getModel().allocatedHosts(), latest.get());
            builtModelVersions.add(latestModelVersion);
        }

        // load old model versions
        versions = versionsToBuild(versions, wantedNodeVespaVersion, majorVersion, allocatedHosts);
        for (Version version : versions) {
            if (latest.isPresent() && version.equals(latest.get())) continue; // already loaded

            try {
                MODELRESULT modelVersion = buildModelVersion(modelFactoryRegistry.getFactory(version),
                                                             applicationPackage,
                                                             applicationId,
                                                             wantedDockerImageRepository,
                                                             wantedNodeVespaVersion);
                allocatedHosts.add(modelVersion.getModel().allocatedHosts(), version);
                builtModelVersions.add(modelVersion);
            } catch (RuntimeException e) {
                // allow failure to create old config models if there is a validation override that allow skipping old
                // config models or we're manually deploying
                if (builtModelVersions.size() > 0 &&
                    ( builtModelVersions.get(0).getModel().skipOldConfigModels(now) || zone().environment().isManuallyDeployed()))
                    log.log(Level.INFO, applicationId + ": Failed to build version " + version +
                                        ", but allow failure due to validation override or manual deployment");
                else {
                    log.log(Level.SEVERE, applicationId + ": Failed to build version " + version);
                    throw e;
                }
            }
        }
        return builtModelVersions;
    }

    private Set<Version> versionsToBuild(Set<Version> versions, Version wantedVersion, int majorVersion,
                                         AllocatedHostsFromAllModels allocatedHosts) {
        // TODO: This won't find nodes allocated to the application only on older model versions.
        //       Correct would be to determine this from all active nodes.
        versions = keepThoseUsedOn(allocatedHosts.toAllocatedHosts(), versions);

        // Make sure we build wanted version if we are building models for this major version and we are on hosted vespa
        // If not on hosted vespa, we do not want to try to build this version, since we have only one version (the latest)
        if (hosted && wantedVersion.getMajor() == majorVersion)
            versions.add(wantedVersion);
        return versions;
    }

    private Set<Version> keepMajorVersion(int majorVersion, Set<Version> versions) {
        return versions.stream().filter(v -> v.getMajor() == majorVersion).collect(Collectors.toSet());
    }

    private Set<Version> keepUpToMajorVersion(int majorVersion, Set<Version> versions) {
        return versions.stream().filter(v -> v.getMajor() <= majorVersion).collect(Collectors.toSet());
    }

    private Version findLatest(Set<Version> versionSet) {
        List<Version> versionList = new ArrayList<>(versionSet);
        Collections.sort(versionList);
        return versionList.get(versionList.size() - 1);
    }

    /** Returns the subset of the given versions which are in use on these hosts */
    private Set<Version> keepThoseUsedOn(AllocatedHosts hosts, Set<Version> versions) {
        return versions.stream().filter(version -> isUsedOn(hosts, version)).collect(Collectors.toSet());
    }

    private boolean isUsedOn(AllocatedHosts hosts, Version version) {
        return hosts.getHosts().stream()
                               .anyMatch(host -> host.version().isPresent() && host.version().get().equals(version));
    }

    protected abstract MODELRESULT buildModelVersion(ModelFactory modelFactory, ApplicationPackage applicationPackage,
                                                     ApplicationId applicationId, Optional<DockerImage> dockerImageRepository,
                                                     Version wantedNodeVespaVersion);

    /**
     * Returns a host provisioner returning the previously allocated hosts if available and when on hosted Vespa,
     * returns empty otherwise, which may either mean that no hosts are allocated or that we are running
     * non-hosted and should default to use hosts defined in the application package, depending on context
     */
    HostProvisioner createStaticProvisioner(ApplicationPackage applicationPackage,
                                            ApplicationId applicationId,
                                            Provisioned provisioned) {
        Optional<AllocatedHosts> allocatedHosts = applicationPackage.getAllocatedHosts();
        if (hosted && allocatedHosts.isPresent())
            return createStaticProvisionerForHosted(allocatedHosts.get(), createNodeRepositoryProvisioner(applicationId, provisioned).get());
        return DeployState.getDefaultModelHostProvisioner(applicationPackage);
    }

    /**
     * Returns a host provisioner returning the previously allocated hosts
     */
    HostProvisioner createStaticProvisionerForHosted(AllocatedHosts allocatedHosts, HostProvisioner nodeRepositoryProvisioner) {
        return new StaticProvisioner(allocatedHosts, nodeRepositoryProvisioner);
    }

    Optional<HostProvisioner> createNodeRepositoryProvisioner(ApplicationId applicationId, Provisioned provisioned) {
        return hostProvisionerProvider.getHostProvisioner().map(
                provisioner -> new ProvisionerAdapter(provisioner, applicationId, provisioned));
    }

}
