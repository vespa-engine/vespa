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
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.GenerationCounter;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.model.SuperModelConfigProvider;
import com.yahoo.vespa.flags.FlagSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Provides a SuperModel - a model of all application instances,  and makes it stays
 * up to date as applications are added, redeployed, and removed.
 */
public class SuperModelManager implements SuperModelProvider {
    private final Zone zone;

    private final Object monitor = new Object();
    private final FlagSource flagSource;
    private SuperModelConfigProvider superModelConfigProvider;  // Guarded by 'this' monitor
    private final List<SuperModelListener> listeners = new ArrayList<>();  // Guarded by 'this' monitor

    // Generation of the super model
    private long generation;
    private final long masterGeneration; // ConfigserverConfig's generation
    private final GenerationCounter generationCounter;

    // The initial set of applications to be deployed on bootstrap.
    private Optional<Set<ApplicationId>> bootstrapApplicationSet = Optional.empty();

    @Inject
    public SuperModelManager(ConfigserverConfig configserverConfig,
                             NodeFlavors nodeFlavors,
                             GenerationCounter generationCounter,
                             FlagSource flagSource) {
        this.flagSource = flagSource;
        this.zone = new Zone(configserverConfig, nodeFlavors);
        this.generationCounter = generationCounter;
        this.masterGeneration = configserverConfig.masterGeneration();
        makeNewSuperModelConfigProvider(new SuperModel());
    }

    @Override
    public SuperModel getSuperModel() {
        synchronized (monitor) {
            return superModelConfigProvider.getSuperModel();
        }
    }

    public SuperModelConfigProvider getSuperModelConfigProvider() {
        synchronized (monitor) {
            return superModelConfigProvider;
        }
    }

    public long getGeneration() {
        synchronized (monitor) {
            return generation;
        }
    }

    @Override
    public void registerListener(SuperModelListener listener) {
        synchronized (monitor) {
            listeners.add(listener);
            SuperModel superModel = superModelConfigProvider.getSuperModel();
            superModel.getAllApplicationInfos().forEach(application -> listener.applicationActivated(superModel, application));

            if (superModel.isComplete()) {
                listener.notifyOfCompleteness(superModel);
            }
        }
    }

    public Zone getZone() {
        return zone;
    }

    public void configActivated(ApplicationSet applicationSet) {
        synchronized (monitor) {
            // TODO: Should supermodel care about multiple versions?
            ApplicationInfo applicationInfo = applicationSet
                    .getForVersionOrLatest(Optional.empty(), Instant.now())
                    .toApplicationInfo();

            SuperModel oldSuperModel = superModelConfigProvider.getSuperModel();
            SuperModel newSuperModel = oldSuperModel
                    .cloneAndSetApplication(applicationInfo, isComplete(oldSuperModel));

            generationCounter.increment();
            makeNewSuperModelConfigProvider(newSuperModel);
            listeners.forEach(listener -> listener.applicationActivated(newSuperModel, applicationInfo));

            if (!oldSuperModel.isComplete() && newSuperModel.isComplete()) {
                for (var listener : listeners) {
                    listener.notifyOfCompleteness(newSuperModel);
                }
            }
        }
    }

    public void applicationRemoved(ApplicationId applicationId) {
        synchronized (monitor) {
            SuperModel newSuperModel = this.superModelConfigProvider
                    .getSuperModel()
                    .cloneAndRemoveApplication(applicationId);
            generationCounter.increment();
            makeNewSuperModelConfigProvider(newSuperModel);
            listeners.forEach(listener -> listener.applicationRemoved(newSuperModel, applicationId));
        }
    }

    public void setBootstrapApplicationSet(Set<ApplicationId> bootstrapApplicationSet) {
        synchronized (monitor) {
            this.bootstrapApplicationSet = Optional.of(bootstrapApplicationSet);

            SuperModel superModel = superModelConfigProvider.getSuperModel();
            if (!superModel.isComplete() && isComplete(superModel)) {
                // We do NOT increment the generation since completeness is not part of the config:
                // generationCounter.increment()

                SuperModel newSuperModel = superModel.cloneAsComplete();
                makeNewSuperModelConfigProvider(newSuperModel);
                listeners.forEach(listener -> listener.notifyOfCompleteness(newSuperModel));
            }
        }
    }

    /** Returns freshly calculated value of isComplete. */
    private boolean isComplete(SuperModel currentSuperModel) {
        if (currentSuperModel.isComplete()) return true;
        if (bootstrapApplicationSet.isEmpty()) return false;

        Set<ApplicationId> currentApplicationIds = superModelConfigProvider.getSuperModel().getApplicationIds();
        if (currentApplicationIds.size() < bootstrapApplicationSet.get().size()) return false;
        return currentApplicationIds.containsAll(bootstrapApplicationSet.get());
    }

    private void makeNewSuperModelConfigProvider(SuperModel newSuperModel) {
        generation = masterGeneration + generationCounter.get();
        superModelConfigProvider = new SuperModelConfigProvider(newSuperModel, zone, flagSource);
    }
}
