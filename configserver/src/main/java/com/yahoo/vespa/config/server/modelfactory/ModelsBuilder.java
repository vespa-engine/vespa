// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.modelfactory;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.api.Provisioned;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationLockException;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.config.provision.TransientException;
import com.yahoo.config.provision.Zone;
import com.yahoo.lang.SettableOptional;
import com.yahoo.vespa.config.server.http.InternalServerException;
import com.yahoo.vespa.config.server.http.UnknownVespaVersionException;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.provision.ProvisionerAdapter;
import com.yahoo.vespa.config.server.provision.StaticProvisioner;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
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

    ModelsBuilder(ModelFactoryRegistry modelFactoryRegistry, ConfigserverConfig configserverConfig,
                  Zone zone, HostProvisionerProvider hostProvisionerProvider) {
        this.modelFactoryRegistry = modelFactoryRegistry;
        this.configserverConfig = configserverConfig;
        this.hosted = configserverConfig.hostedVespa();
        this.zone = zone;
        this.hostProvisionerProvider = hostProvisionerProvider;
    }

    /** Returns the zone this is running in */
    protected Zone zone() { return zone; }

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
                                         SettableOptional<AllocatedHosts> allocatedHosts,
                                         Instant now) {
        Instant start = Instant.now();
        log.log(Level.FINE, "Will build models for " + applicationId);
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
            catch (OutOfCapacityException | ApplicationLockException | TransientException e) {
                // Don't wrap this exception, and don't try to load other model versions as this is (most likely)
                // caused by the state of the system, not the model version/application combination
                throw e;
            }
            catch (RuntimeException e) {
                if (shouldSkipCreatingMajorVersionOnError(majorVersions, majorVersion)) {
                    log.log(Level.INFO, applicationId + ": Skipping major version " + majorVersion, e);
                } else  {
                    if (e instanceof NullPointerException || e instanceof NoSuchElementException | e instanceof UncheckedTimeoutException) {
                        log.log(Level.WARNING, "Unexpected error when building model ", e);
                        throw new InternalServerException(applicationId + ": Error loading model", e);
                    } else {
                        log.log(Level.WARNING, "Input error when building model ", e);
                        throw new IllegalArgumentException(applicationId + ": Error loading model", e);
                    }
                }
            }
        }
        log.log(Level.FINE, "Done building models for " + applicationId + ". Built models for versions " +
                            allApplicationModels.stream()
                                                .map(result -> result.getModel().version())
                                                .map(Version::toFullString)
                                                .collect(Collectors.toSet()) +
                            " in " + Duration.between(start, Instant.now()));
        return allApplicationModels;
    }

    private boolean shouldSkipCreatingMajorVersionOnError(List<Integer> majorVersions, Integer majorVersion) {
        if (majorVersion.equals(Collections.min(majorVersions))) return false;
        // Note: This needs to be updated when we no longer want to support successfully deploying
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
                                                 SettableOptional<AllocatedHosts> allocatedHosts,
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
                                                               wantedNodeVespaVersion,
                                                               allocatedHosts.asOptional());
            allocatedHosts.set(latestModelVersion.getModel().allocatedHosts()); // Update with additional clusters allocated
            builtModelVersions.add(latestModelVersion);
        }

        // load old model versions
        versions = versionsToBuild(versions, wantedNodeVespaVersion, majorVersion, allocatedHosts.get());
        // TODO: We use the allocated hosts from the newest version when building older model versions.
        // This is correct except for the case where an old model specifies a cluster which the new version
        // does not. In that case we really want to extend the set of allocated hosts to include those of that
        // cluster as well. To do that, create a new provisioner which uses static provisioning for known
        // clusters and the node repository provisioner as fallback.
        for (Version version : versions) {
            if (latest.isPresent() && version.equals(latest.get())) continue; // already loaded

            try {
                MODELRESULT modelVersion = buildModelVersion(modelFactoryRegistry.getFactory(version),
                                                             applicationPackage,
                                                             applicationId,
                                                             wantedDockerImageRepository,
                                                             wantedNodeVespaVersion,
                                                             allocatedHosts.asOptional());
                allocatedHosts.set(modelVersion.getModel().allocatedHosts()); // Update with additional clusters allocated
                builtModelVersions.add(modelVersion);
            } catch (RuntimeException e) {
                // allow failure to create old config models if there is a validation override that allow skipping old
                // config models (which is always true for manually deployed zones)
                if (builtModelVersions.size() > 0 && builtModelVersions.get(0).getModel().skipOldConfigModels(now))
                    log.log(Level.INFO, applicationId + ": Failed to build version " + version +
                                        ", but allow failure due to validation override ´skipOldConfigModels´");
                else {
                    log.log(Level.SEVERE, applicationId + ": Failed to build version " + version);
                    throw e;
                }
            }
        }
        return builtModelVersions;
    }

    private Set<Version> versionsToBuild(Set<Version> versions, Version wantedVersion, int majorVersion, AllocatedHosts allocatedHosts) {
        if (configserverConfig.buildMinimalSetOfConfigModels())
            versions = keepThoseUsedOn(allocatedHosts, versions);

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
                                                     Version wantedNodeVespaVersion, Optional<AllocatedHosts> allocatedHosts);

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
