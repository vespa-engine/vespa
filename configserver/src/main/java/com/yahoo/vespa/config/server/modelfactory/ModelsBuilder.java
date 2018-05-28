// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.modelfactory;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationLockException;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.config.provision.Rotation;
import com.yahoo.config.provision.Version;
import com.yahoo.config.provision.Zone;
import com.yahoo.lang.SettableOptional;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.server.ConfigServerSpec;
import com.yahoo.vespa.config.server.deploy.ModelContextImpl;
import com.yahoo.vespa.config.server.http.InternalServerException;
import com.yahoo.vespa.config.server.http.UnknownVespaVersionException;
import com.yahoo.vespa.config.server.provision.StaticProvisioner;

import java.net.URI;
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

    /** True if we are running in hosted mode */
    private final boolean hosted;

    private final Zone zone;

    protected ModelsBuilder(ModelFactoryRegistry modelFactoryRegistry, boolean hosted, Zone zone) {
        this.modelFactoryRegistry = modelFactoryRegistry;
        this.hosted = hosted;
        this.zone = zone;
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
                                         com.yahoo.component.Version wantedNodeVespaVersion, 
                                         ApplicationPackage applicationPackage,
                                         SettableOptional<AllocatedHosts> allocatedHosts,
                                         Instant now) {
        Set<Version> versions = modelFactoryRegistry.allVersions();

        // If the application specifies a major, load models only for that
        Optional<Integer> requestedMajorVersion = applicationPackage.getMajorVersion();
        if (requestedMajorVersion.isPresent())
            versions = filterByMajorVersion(requestedMajorVersion.get(), versions);

        // Load models by one major version at the time as new major versions are allowed to be non-loadable
        // in the case where an existing application is incompatible with a new major version
        // (which is possible by the definition of major)
        List<Integer> majorVersions = versions.stream()
                                              .map(Version::getMajor)
                                              .distinct()
                                              .sorted(Comparator.reverseOrder())
                                              .collect(Collectors.toList());

        List<MODELRESULT> allApplicationModels = new ArrayList<>();
        for (int i = 0; i < majorVersions.size(); i++) {
            try {
                allApplicationModels.addAll(buildModelVersion(filterByMajorVersion(majorVersions.get(i), versions),
                                                              applicationId, wantedNodeVespaVersion, applicationPackage, 
                                                              allocatedHosts, now));

                // skip old config models if requested after we have found a major version which works
                if (allApplicationModels.size() > 0 && allApplicationModels.get(0).getModel().skipOldConfigModels(now))
                    break;
            }
            catch (OutOfCapacityException | ApplicationLockException e) {
                // Don't wrap this exception, and don't try to load other model versions as this is (most likely)
                // caused by the state of the system, not the model version/application combination
                throw e;
            }
            catch (RuntimeException e) {
                boolean isOldestMajor = i == majorVersions.size() - 1;
                if (isOldestMajor) {
                    if (e instanceof NullPointerException || e instanceof NoSuchElementException | e instanceof UncheckedTimeoutException) {
                        log.log(LogLevel.WARNING, "Unexpected error when building model ", e);
                        throw new InternalServerException(applicationId + ": Error loading model", e);
                    } else {
                        log.log(LogLevel.WARNING, "Input error when building model ", e);
                        throw new IllegalArgumentException(applicationId + ": Error loading model", e);
                    }
                } else {
                    log.log(Level.INFO, applicationId + ": Skipping major version " + majorVersions.get(i), e);
                }
            }
        }
        return allApplicationModels;
    }

    private List<MODELRESULT> buildModelVersion(Set<Version> versions, ApplicationId applicationId,
                                                com.yahoo.component.Version wantedNodeVespaVersion, 
                                                ApplicationPackage applicationPackage,
                                                SettableOptional<AllocatedHosts> allocatedHosts,
                                                Instant now) {
        Version latest = findLatest(versions);
        // load latest application version
        MODELRESULT latestModelVersion = buildModelVersion(modelFactoryRegistry.getFactory(latest), 
                                                           applicationPackage, 
                                                           applicationId, 
                                                           wantedNodeVespaVersion, 
                                                           allocatedHosts.asOptional(),
                                                           now);
        allocatedHosts.set(latestModelVersion.getModel().allocatedHosts()); // Update with additional clusters allocated
        
        if (latestModelVersion.getModel().skipOldConfigModels(now))
            return Collections.singletonList(latestModelVersion);

        // load old model versions
        List<MODELRESULT> allApplicationVersions = new ArrayList<>();
        allApplicationVersions.add(latestModelVersion);

        if (zone().environment() == Environment.dev)
            versions = keepThoseUsedOn(allocatedHosts.get(), versions);

        // TODO: We use the allocated hosts from the newest version when building older model versions.
        // This is correct except for the case where an old model specifies a cluster which the new version
        // does not. In that case we really want to extend the set of allocated hosts to include those of that
        // cluster as well. To do that, create a new provisioner which uses static provisioning for known
        // clusters and the node repository provisioner as fallback.
        for (Version version : versions) {
            if (version.equals(latest)) continue; // already loaded

            MODELRESULT modelVersion = buildModelVersion(modelFactoryRegistry.getFactory(version),
                                                         applicationPackage,
                                                         applicationId,
                                                         wantedNodeVespaVersion,
                                                         allocatedHosts.asOptional(),
                                                         now);
            allocatedHosts.set(modelVersion.getModel().allocatedHosts()); // Update with additional clusters allocated
            allApplicationVersions.add(modelVersion);
        }
        return allApplicationVersions;
    }

    private Set<Version> filterByMajorVersion(int majorVersion, Set<Version> versions) {
        Set<Version> filteredVersions = versions.stream().filter(v -> v.getMajor() == majorVersion).collect(Collectors.toSet());
        if (filteredVersions.isEmpty())
            throw new UnknownVespaVersionException("No Vespa versions matching major version " + majorVersion + " are present");
        return filteredVersions;
    }

    private Version findLatest(Set<Version> versionSet) {
        List<Version> versionList = new ArrayList<>(versionSet);
        Collections.sort(versionList);
        return versionList.get(versionList.size() - 1);
    }

    /** Returns the subset of the given versions which are in use on these hosts */
    private Set<Version> keepThoseUsedOn(AllocatedHosts hosts, Set<Version> versions) {
        return versions.stream().filter(version -> mayBeUsedOn(hosts, version)).collect(Collectors.toSet());
    }

    private boolean mayBeUsedOn(AllocatedHosts hosts, Version version) {
        com.yahoo.component.Version v = new com.yahoo.component.Version(version.toString());
        return hosts.getHosts().stream()
                               .anyMatch(host -> ! host.version().isPresent() || host.version().get().equals(v));
    }

    protected abstract MODELRESULT buildModelVersion(ModelFactory modelFactory, ApplicationPackage applicationPackage,
                                                     ApplicationId applicationId, 
                                                     com.yahoo.component.Version wantedNodeVespaVersion,
                                                     Optional<AllocatedHosts> allocatedHosts,
                                                     Instant now);

    protected ModelContext.Properties createModelContextProperties(ApplicationId applicationId,
                                                                   ConfigserverConfig configserverConfig,
                                                                   Zone zone,
                                                                   Set<Rotation> rotations) {
        return new ModelContextImpl.Properties(applicationId,
                                               configserverConfig.multitenant(),
                                               ConfigServerSpec.fromConfig(configserverConfig),
                                               HostName.from(configserverConfig.loadBalancerAddress()),
                                               configserverConfig.ztsUrl() != null ? URI.create(configserverConfig.ztsUrl()) : null,
                                               configserverConfig.athenzDnsSuffix(),
                                               configserverConfig.hostedVespa(),
                                               zone,
                                               rotations);
    }

    /** 
     * Returns a host provisioner returning the previously allocated hosts if available and when on hosted Vespa,
     * returns empty otherwise, which may either mean that no hosts are allocated or that we are running
     * non-hosted and should default to use hosts defined in the application package, depending on context
     */
    protected Optional<HostProvisioner> createStaticProvisioner(Optional<AllocatedHosts> allocatedHosts) {
        if (hosted && allocatedHosts.isPresent())
            return Optional.of(new StaticProvisioner(allocatedHosts.get()));
        return Optional.empty();
    }

}
