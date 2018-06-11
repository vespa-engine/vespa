// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.config.server;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.model.api.SuperModelListener;
import com.yahoo.config.model.api.SuperModelProvider;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.GenerationCounter;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.model.SuperModelConfigProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Provides a SuperModel - a model of all application instances,  and makes it stays
 * up to date as applications are added, redeployed, and removed.
 */
public class SuperModelManager implements SuperModelProvider {
    private final Zone zone;
    private SuperModelConfigProvider superModelConfigProvider;  // Guarded by 'this' monitor
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
        makeNewSuperModelConfigProvider(new SuperModel());
    }

    @Override
    public synchronized SuperModel getSuperModel() {
        return superModelConfigProvider.getSuperModel();
    }

    public synchronized SuperModelConfigProvider getSuperModelConfigProvider() {
        return superModelConfigProvider;
    }

    public synchronized long getGeneration() {
        return generation;
    }

    @Override
    public synchronized SuperModel snapshot(SuperModelListener listener) {
        listeners.add(listener);
        return superModelConfigProvider.getSuperModel();
    }

    @Override
    public Zone getZone() {
        return zone;
    }

    public synchronized void configActivated(ApplicationSet applicationSet) {
        // TODO: Should supermodel care about multiple versions?
        ApplicationInfo applicationInfo = applicationSet
                .getForVersionOrLatest(Optional.empty(), Instant.now())
                .toApplicationInfo();

        SuperModel newSuperModel = this.superModelConfigProvider
                .getSuperModel()
                .cloneAndSetApplication(applicationInfo);
        makeNewSuperModelConfigProvider(newSuperModel);
        listeners.stream().forEach(listener ->
                listener.applicationActivated(newSuperModel, applicationInfo));
    }

    public synchronized void applicationRemoved(ApplicationId applicationId) {
        SuperModel newSuperModel = this.superModelConfigProvider
                .getSuperModel()
                .cloneAndRemoveApplication(applicationId);
        makeNewSuperModelConfigProvider(newSuperModel);
        listeners.stream().forEach(listener ->
                listener.applicationRemoved(newSuperModel, applicationId));
    }

    private void makeNewSuperModelConfigProvider(SuperModel newSuperModel) {
        generation = masterGeneration + generationCounter.get();
        superModelConfigProvider = new SuperModelConfigProvider(newSuperModel, zone);
    }
}
