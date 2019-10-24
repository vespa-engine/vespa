// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author freva
 */
public class ApplicationIdSnapshot {
    private final Map<TenantName, Map<ApplicationName, List<InstanceName>>> instanceByApplicationByTenantName;

    public ApplicationIdSnapshot(Map<TenantName, Map<ApplicationName, List<InstanceName>>> instanceByApplicationByTenantName) {
        this.instanceByApplicationByTenantName = instanceByApplicationByTenantName.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().entrySet().stream()
                        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, f -> List.copyOf(f.getValue())))));
    }

    public List<TenantName> tenants() {
        return List.copyOf(instanceByApplicationByTenantName.keySet());
    }

    public List<ApplicationName> applications(TenantName tenantName) {
        return Optional.ofNullable(instanceByApplicationByTenantName.get(tenantName))
                .map(a -> List.copyOf(a.keySet()))
                .orElseGet(List::of);
    }

    public List<InstanceName> instances(TenantName tenantName, ApplicationName applicationName) {
        return instanceByApplicationByTenantName.getOrDefault(tenantName, Map.of())
                .getOrDefault(applicationName, List.of());
    }


    public static class Builder {
        private final Map<TenantName, Map<ApplicationName, List<InstanceName>>> instanceByApplicationByTenantName = new HashMap<>();

        public Builder add(TenantName tenantName) {
            instanceByApplicationByTenantName.computeIfAbsent(tenantName, t -> new HashMap<>());
            return this;
        }

        public Builder add(TenantName tenantName, ApplicationName applicationName) {
            instanceByApplicationByTenantName.computeIfAbsent(tenantName, t -> new HashMap<>())
                    .computeIfAbsent(applicationName, a -> new ArrayList<>());
            return this;
        }

        public Builder add(TenantName tenantName, ApplicationName applicationName, InstanceName instanceName) {
            add(tenantName, applicationName);
            instanceByApplicationByTenantName.get(tenantName).get(applicationName).add(instanceName);
            return this;
        }

        public Builder add(ApplicationId applicationId) {
            return add(applicationId.tenant(), applicationId.application(), applicationId.instance());
        }

        public ApplicationIdSnapshot build() {
            return new ApplicationIdSnapshot(instanceByApplicationByTenantName);
        }
    }
}
