// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SuperModel {
    private final Map<TenantName, Map<ApplicationId, ApplicationInfo>> models;

    public SuperModel() {
        this.models = Collections.emptyMap();
    }

    public SuperModel(Map<TenantName, Map<ApplicationId, ApplicationInfo>> models) {
        this.models = models;
    }

    /**
     * Do NOT mutate the returned map.
     * TODO: Make the returned map immutable (and type to Map&lt;ApplicationId, ApplicationInfo&gt;)
     */
    public Map<TenantName, Map<ApplicationId, ApplicationInfo>> getAllModels() {
        return models;
    }

    public List<ApplicationInfo> getAllApplicationInfos() {
        return models.values().stream().flatMap(entry -> entry.values().stream()).collect(Collectors.toList());
    }

    public Optional<ApplicationInfo> getApplicationInfo(ApplicationId applicationId) {
        Map<ApplicationId, ApplicationInfo> tenantInfo = models.get(applicationId.tenant());
        if (tenantInfo == null) {
            return Optional.empty();
        }

        ApplicationInfo applicationInfo = tenantInfo.get(applicationId);
        if (applicationInfo == null) {
            return Optional.empty();
        }

        return Optional.of(applicationInfo);
    }

    public SuperModel cloneAndSetApplication(ApplicationInfo application) {
        TenantName tenant = application.getApplicationId().tenant();
        Map<TenantName, Map<ApplicationId, ApplicationInfo>> newModels = cloneModels(models);
        if (!newModels.containsKey(tenant)) {
            // New application has been activated
            newModels.put(tenant, new LinkedHashMap<>());
        } else {
            // Application has been redeployed
        }

        newModels.get(tenant).put(application.getApplicationId(), application);

        return new SuperModel(newModels);
    }

    public SuperModel cloneAndRemoveApplication(ApplicationId applicationId) {
        Map<TenantName, Map<ApplicationId, ApplicationInfo>> newModels = cloneModels(models);
        if (newModels.containsKey(applicationId.tenant())) {
            newModels.get(applicationId.tenant()).remove(applicationId);
            if (newModels.get(applicationId.tenant()).isEmpty()) {
                newModels.remove(applicationId.tenant());
            }
        }

        return new SuperModel(newModels);
    }

    private static Map<TenantName, Map<ApplicationId, ApplicationInfo>> cloneModels(
            Map<TenantName, Map<ApplicationId, ApplicationInfo>> models) {
        Map<TenantName, Map<ApplicationId, ApplicationInfo>> newModels = new LinkedHashMap<>();
        for (Map.Entry<TenantName, Map<ApplicationId, ApplicationInfo>> entry : models.entrySet()) {
            Map<ApplicationId, ApplicationInfo> appMap = new LinkedHashMap<>();
            newModels.put(entry.getKey(), appMap);
            for (Map.Entry<ApplicationId, ApplicationInfo> appEntry : entry.getValue().entrySet()) {
                appMap.put(appEntry.getKey(), appEntry.getValue());
            }
        }

        return newModels;
    }
}
