// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationStore;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterId;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;

import java.time.Instant;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import static java.util.Objects.requireNonNull;

/**
 * Threadsafe.
 *
 * @author jonmv
 */
public class ApplicationStoreMock implements ApplicationStore {

    private static final byte[] tombstone = new byte[0];

    private final Map<ApplicationId, Map<ApplicationVersion, byte[]>> store = new ConcurrentHashMap<>();
    private final Map<DeploymentId, byte[]> devStore = new ConcurrentHashMap<>();
    private final Map<ApplicationId, Map<Long, byte[]>> diffs = new ConcurrentHashMap<>();
    private final Map<DeploymentId, Map<Long, byte[]>> devDiffs = new ConcurrentHashMap<>();
    private final Map<ApplicationId, NavigableMap<Instant, byte[]>> meta = new ConcurrentHashMap<>();
    private final Map<DeploymentId, NavigableMap<Instant, byte[]>> metaManual = new ConcurrentHashMap<>();

    private static ApplicationId appId(TenantName tenant, ApplicationName application) {
        return ApplicationId.from(tenant, application, InstanceName.defaultName());
    }

    private static ApplicationId testerId(TenantName tenant, ApplicationName application) {
        return TesterId.of(appId(tenant, application)).id();
    }

    @Override
    public byte[] get(DeploymentId deploymentId, ApplicationVersion applicationVersion) {
        if (applicationVersion.isDeployedDirectly())
            return requireNonNull(devStore.get(deploymentId));

        TenantAndApplicationId tenantAndApplicationId = TenantAndApplicationId.from(deploymentId.applicationId());
        byte[] bytes = store.get(appId(tenantAndApplicationId.tenant(), tenantAndApplicationId.application())).get(applicationVersion);
        if (bytes == null)
            throw new IllegalArgumentException("No application package found for " + tenantAndApplicationId +
                                               " with version " + applicationVersion.id());
        return bytes;
    }

    @Override
    public Optional<byte[]> getDiff(TenantName tenantName, ApplicationName applicationName, long buildNumber) {
        return Optional.ofNullable(diffs.get(appId(tenantName, applicationName))).map(map -> map.get(buildNumber));
    }

    @Override
    public void pruneDiffs(TenantName tenantName, ApplicationName applicationName, long beforeBuildNumber) {
        Optional.ofNullable(diffs.get(appId(tenantName, applicationName)))
                .ifPresent(map -> map.keySet().removeIf(buildNumber -> buildNumber < beforeBuildNumber));
    }

    @Override
    public Optional<byte[]> find(TenantName tenant, ApplicationName application, long buildNumber) {
        return store.getOrDefault(appId(tenant, application), Map.of()).entrySet().stream()
                    .filter(kv -> kv.getKey().buildNumber().orElse(Long.MIN_VALUE) == buildNumber)
                    .map(Map.Entry::getValue)
                    .findFirst();
    }

    @Override
    public void put(TenantName tenant, ApplicationName application, ApplicationVersion applicationVersion, byte[] applicationPackage, byte[] diff) {
        store.computeIfAbsent(appId(tenant, application), __ -> new ConcurrentHashMap<>()).put(applicationVersion, applicationPackage);
        applicationVersion.buildNumber().ifPresent(buildNumber ->
                diffs.computeIfAbsent(appId(tenant, application), __ -> new ConcurrentHashMap<>()).put(buildNumber, diff));
    }

    @Override
    public boolean prune(TenantName tenant, ApplicationName application, ApplicationVersion oldestToRetain) {
        return    store.containsKey(appId(tenant, application))
               && store.get(appId(tenant, application)).keySet().removeIf(version -> version.compareTo(oldestToRetain) < 0);
    }

    @Override
    public void removeAll(TenantName tenant, ApplicationName application) {
        store.remove(appId(tenant, application));
    }

    @Override
    public byte[] getTester(TenantName tenant, ApplicationName application, ApplicationVersion applicationVersion) {
        return requireNonNull(store.get(testerId(tenant, application)).get(applicationVersion));
    }

    @Override
    public void putTester(TenantName tenant, ApplicationName application, ApplicationVersion applicationVersion, byte[] testerPackage) {
        store.computeIfAbsent(testerId(tenant, application), key -> new ConcurrentHashMap<>())
                .put(applicationVersion, testerPackage);
    }

    @Override
    public boolean pruneTesters(TenantName tenant, ApplicationName application, ApplicationVersion oldestToRetain) {
        return    store.containsKey(testerId(tenant, application))
               && store.get(testerId(tenant, application)).keySet().removeIf(version -> version.compareTo(oldestToRetain) < 0);
    }

    @Override
    public void removeAllTesters(TenantName tenant, ApplicationName application) {
        store.remove(testerId(tenant, application));
    }

    @Override
    public Optional<byte[]> getDevDiff(DeploymentId deploymentId, long buildNumber) {
        return Optional.ofNullable(devDiffs.get(deploymentId)).map(map -> map.get(buildNumber));
    }

    @Override
    public void pruneDevDiffs(DeploymentId deploymentId, long beforeBuildNumber) {
        Optional.ofNullable(devDiffs.get(deploymentId))
                .ifPresent(map -> map.keySet().removeIf(buildNumber -> buildNumber < beforeBuildNumber));
    }

    @Override
    public void putDev(DeploymentId deploymentId, ApplicationVersion applicationVersion, byte[] applicationPackage, byte[] diff) {
        devStore.put(deploymentId, applicationPackage);
        applicationVersion.buildNumber().ifPresent(buildNumber ->
                devDiffs.computeIfAbsent(deploymentId, __ -> new ConcurrentHashMap<>()).put(buildNumber, diff));
    }

    @Override
    public void putMeta(TenantName tenant, ApplicationName application, Instant now, byte[] metaZip) {
        meta.putIfAbsent(appId(tenant, application), new ConcurrentSkipListMap<>());
        meta.get(appId(tenant, application)).put(now, metaZip);
    }

    @Override
    public void putMetaTombstone(TenantName tenant, ApplicationName application, Instant now) {
        putMeta(tenant, application, now, tombstone);
    }

    @Override
    public void putMeta(DeploymentId id, Instant now, byte[] metaZip) {
        metaManual.computeIfAbsent(id, __ -> new ConcurrentSkipListMap<>()).put(now, metaZip);
    }

    @Override
    public void putMetaTombstone(DeploymentId id, Instant now) {
        putMeta(id, now, tombstone);
    }

    @Override
    public void pruneMeta(Instant oldest) {
        for (ApplicationId id : meta.keySet()) {
            Instant activeAtOldest = meta.get(id).lowerKey(oldest);
            if (activeAtOldest != null)
                meta.get(id).headMap(activeAtOldest).clear();
            if (meta.get(id).lastKey().isBefore(oldest) && meta.get(id).lastEntry().getValue() == tombstone)
                meta.remove(id);
        }
    }

    public NavigableMap<Instant, byte[]> getMeta(ApplicationId id) { return meta.get(id); }

    public NavigableMap<Instant, byte[]> getMeta(DeploymentId id) { return metaManual.get(id); }

}
