// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.modelfactory;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.config.provision.Rotation;
import com.yahoo.config.provision.Version;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.server.ConfigServerSpec;
import com.yahoo.vespa.config.server.deploy.ModelContextImpl;
import com.yahoo.vespa.config.server.http.UnknownVespaVersionException;

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

    protected ModelsBuilder(ModelFactoryRegistry modelFactoryRegistry) {
        this.modelFactoryRegistry = modelFactoryRegistry;
    }

    public List<MODELRESULT> buildModels(ApplicationId applicationId, 
                                         com.yahoo.component.Version wantedNodeVespaVersion, 
                                         ApplicationPackage applicationPackage,
                                         Instant now) {
        Set<Version> versions = modelFactoryRegistry.allVersions();

        // If the application specifies a major, load models only for that
        Optional<Integer> requestedMajorVersion = applicationPackage.getMajorVersion();
        if (requestedMajorVersion.isPresent())
            versions = filterByMajorVersion(requestedMajorVersion.get(), versions);

        // Load models by one major version at the time as new major versions are allowed to be unloadable
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
                                                              applicationId, wantedNodeVespaVersion, applicationPackage, now));

                // skip old config models if requested after we have found a major version which works
                if (allApplicationModels.size() > 0 && allApplicationModels.get(0).getModel().skipOldConfigModels(now))
                    break;
            }
            catch (OutOfCapacityException e) {
                // Don't wrap this exception, and don't try to load other model versions as this is (most likely)
                // caused by the state of the system, not the model version/application combination
                throw e;
            }
            catch (RuntimeException e) {
                boolean isOldestMajor = i == majorVersions.size() - 1;
                if (isOldestMajor) {
                    throw new IllegalArgumentException(applicationId + ": Error loading model", e);
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
                                                Instant now) {
        Version latest = findLatest(versions);
        // load latest application version
        MODELRESULT latestApplicationVersion = buildModelVersion(modelFactoryRegistry.getFactory(latest), 
                                                                 applicationPackage, 
                                                                 applicationId, 
                                                                 wantedNodeVespaVersion, 
                                                                 now);
        if (latestApplicationVersion.getModel().skipOldConfigModels(now)) {
            return Collections.singletonList(latestApplicationVersion);
        }
        else { // load old model versions
            List<MODELRESULT> allApplicationVersions = new ArrayList<>();
            allApplicationVersions.add(latestApplicationVersion);
            for (Version version : versions) {
                if (version.equals(latest)) continue; // already loaded
                allApplicationVersions.add(buildModelVersion(modelFactoryRegistry.getFactory(version), 
                                                             applicationPackage, 
                                                             applicationId, 
                                                             wantedNodeVespaVersion, 
                                                             now));
            }
            return allApplicationVersions;
        }
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

    protected abstract MODELRESULT buildModelVersion(ModelFactory modelFactory, ApplicationPackage applicationPackage,
                                                     ApplicationId applicationId, 
                                                     com.yahoo.component.Version wantedNodeVespaVersion,
                                                     Instant now);

    protected ModelContext.Properties createModelContextProperties(ApplicationId applicationId,
                                                                   ConfigserverConfig configserverConfig,
                                                                   Zone zone,
                                                                   Set<Rotation> rotations) {
        return new ModelContextImpl.Properties(applicationId, 
                                               configserverConfig.multitenant(),
                                               ConfigServerSpec.fromConfig(configserverConfig),
                                               configserverConfig.hostedVespa(),
                                               zone,
                                               rotations);
    }

}
