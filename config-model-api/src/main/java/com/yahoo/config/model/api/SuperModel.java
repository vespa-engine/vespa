// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The SuperModel contains the ApplicationInfo of all active applications
 */
public class SuperModel {

    private final Map<ApplicationId, ApplicationInfo> models;
    private final boolean complete;

    public SuperModel() {
        this(Collections.emptyMap(), false);
    }

    public SuperModel(Map<ApplicationId, ApplicationInfo> models, boolean complete) {
        this.models = models;
        this.complete = complete;
    }

    public Map<TenantName, Set<ApplicationInfo>> getModelsPerTenant() {
        Map<TenantName, Set<ApplicationInfo>> newModels = new LinkedHashMap<>();

        this.models.forEach((key, value) -> {
            if (!newModels.containsKey(key.tenant())) {
                newModels.put(key.tenant(), new LinkedHashSet<>());
            }
            newModels.get(key.tenant()).add(value);
        });
        return newModels;
    }

    public Map<ApplicationId, ApplicationInfo> getModels() {
        return ImmutableMap.copyOf(models);
    }

    public boolean isComplete() { return complete; }

    public List<ApplicationInfo> getAllApplicationInfos() {
        return new ArrayList<>(models.values());
    }

    public Optional<ApplicationInfo> getApplicationInfo(ApplicationId applicationId) {
        ApplicationInfo applicationInfo = models.get(applicationId);
        return applicationInfo == null ? Optional.empty() : Optional.of(applicationInfo);
    }

    public SuperModel cloneAndSetApplication(ApplicationInfo application, boolean complete) {
        Map<ApplicationId, ApplicationInfo> newModels = cloneModels(models);
        newModels.put(application.getApplicationId(), application);
        return new SuperModel(newModels, complete);
    }

    public SuperModel cloneAndRemoveApplication(ApplicationId applicationId) {
        Map<ApplicationId, ApplicationInfo> newModels = cloneModels(models);
        newModels.remove(applicationId);
        return new SuperModel(newModels, complete);
    }

    public SuperModel cloneAsComplete() { return new SuperModel(models, true); }

    public Set<ApplicationId> getApplicationIds() { return models.keySet(); }

    private static Map<ApplicationId, ApplicationInfo> cloneModels(Map<ApplicationId, ApplicationInfo> models) {
        return new LinkedHashMap<>(models);
    }
}
