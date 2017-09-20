// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.config.server;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModelListener;
import com.yahoo.config.model.api.SuperModelProvider;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.GenerationCounter;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.model.SuperModel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provides a SuperModel - a model of all application instances,  and makes it stays
 * up to date as applications are added, redeployed, and removed.
 */
public class SuperModelManager implements SuperModelProvider {
    private final Zone zone;
    private SuperModel superModel;  // Guarded by 'this' monitor
    private final List<SuperModelListener> listeners = new ArrayList<>();  // Guarded by 'this' monitor

    // Generation of the super model
    private long generation;
    private final long masterGeneration; // ConfigserverConfig's generation
    private final GenerationCounter generationCounter;

    @Inject
    public SuperModelManager(ConfigserverConfig configserverConfig,
                             NodeFlavors nodeFlavors,
                             GenerationCounter generationCounter) {
        this.zone = new Zone(configserverConfig, nodeFlavors);
        this.generationCounter = generationCounter;
        this.masterGeneration = configserverConfig.masterGeneration();
        makeNewSuperModel(new HashMap<>());
    }

    public synchronized SuperModel getSuperModel() {
        return superModel;
    }

    public synchronized long getGeneration() {
        return generation;
    }

    @Override
    public synchronized List<ApplicationInfo> snapshot(SuperModelListener listener) {
        listeners.add(listener);
        return superModel.applicationModels().values().stream()
                .flatMap(applications -> applications.values().stream())
                .map(Application::toApplicationInfo)
                .collect(Collectors.toList());
    }

    public synchronized void configActivated(TenantName tenant, ApplicationSet applicationSet) {
        Map<TenantName, Map<ApplicationId, Application>> newModels = createModelCopy();
        if (!newModels.containsKey(tenant)) {
            // New application has been activated
            newModels.put(tenant, new LinkedHashMap<>());
        } else {
            // Application has been redeployed
        }

        // TODO: Should supermodel care about multiple versions?
        Application application = applicationSet.getForVersionOrLatest(Optional.empty(), Instant.now());
        newModels.get(tenant).put(applicationSet.getId(), application);

        makeNewSuperModel(newModels);
        listeners.stream().forEach(listener -> listener.applicationActivated(application.toApplicationInfo()));
    }

    public synchronized void applicationRemoved(ApplicationId applicationId) {
        Map<TenantName, Map<ApplicationId, Application>> newModels = createModelCopy();
        if (newModels.containsKey(applicationId.tenant())) {
            newModels.get(applicationId.tenant()).remove(applicationId);
            if (newModels.get(applicationId.tenant()).isEmpty()) {
                newModels.remove(applicationId.tenant());
            }
        }

        makeNewSuperModel(newModels);
        listeners.stream().forEach(listener -> listener.applicationRemoved(applicationId));
    }

    private void makeNewSuperModel(Map<TenantName, Map<ApplicationId, Application>> newModels) {
        generation = masterGeneration + generationCounter.get();
        superModel = new SuperModel(newModels, zone);
    }

    private Map<TenantName, Map<ApplicationId, Application>> createModelCopy() {
        Map<TenantName, Map<ApplicationId, Application>> currentModels = superModel.applicationModels();
        Map<TenantName, Map<ApplicationId, Application>> newModels = new LinkedHashMap<>();
        for (Map.Entry<TenantName, Map<ApplicationId, Application>> entry : currentModels.entrySet()) {
            Map<ApplicationId, Application> appMap = new LinkedHashMap<>();
            newModels.put(entry.getKey(), appMap);
            for (Map.Entry<ApplicationId, Application> appEntry : entry.getValue().entrySet()) {
                appMap.put(appEntry.getKey(), appEntry.getValue());
            }
        }

        return newModels;
    }
}
