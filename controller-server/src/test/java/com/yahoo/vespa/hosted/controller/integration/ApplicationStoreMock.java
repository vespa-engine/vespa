// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationStore;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterId;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertFalse;

/**
 * Threadsafe.
 *
 * @author jonmv
 */
public class ApplicationStoreMock implements ApplicationStore {

    private final Map<ApplicationId, Map<ApplicationVersion, byte[]>> store = new ConcurrentHashMap<>();
    private final Map<ApplicationId, byte[]> devStore = new ConcurrentHashMap<>();

    @Override
    public byte[] get(ApplicationId application, ApplicationVersion applicationVersion) {
        assertFalse(application.instance().isTester());
        return requireNonNull(store.get(application).get(applicationVersion));
    }

    @Override
    public void put(ApplicationId application, ApplicationVersion applicationVersion, byte[] applicationPackage) {
        assertFalse(application.instance().isTester());
        store.putIfAbsent(application, new ConcurrentHashMap<>());
        store.get(application).put(applicationVersion, applicationPackage);
    }

    @Override
    public boolean prune(ApplicationId application, ApplicationVersion oldestToRetain) {
        assertFalse(application.instance().isTester());
        return    store.containsKey(application)
               && store.get(application).keySet().removeIf(version -> version.compareTo(oldestToRetain) < 0);
    }

    @Override
    public void removeAll(ApplicationId application) {
        store.remove(application);
    }

    @Override
    public byte[] get(TesterId tester, ApplicationVersion applicationVersion) {
        return requireNonNull(store.get(tester.id()).get(applicationVersion));
    }

    @Override
    public void put(TesterId tester, ApplicationVersion applicationVersion, byte[] testerPackage) {
        store.putIfAbsent(tester.id(), new ConcurrentHashMap<>());
        store.get(tester.id()).put(applicationVersion, testerPackage);
    }

    @Override
    public boolean prune(TesterId tester, ApplicationVersion oldestToRetain) {
        return    store.containsKey(tester.id())
               && store.get(tester.id()).keySet().removeIf(version -> version.compareTo(oldestToRetain) < 0);
    }

    @Override
    public void removeAll(TesterId tester) {
        store.remove(tester.id());
    }

    @Override
    public void putDev(ApplicationId application, byte[] applicationPackage) {
        devStore.put(application, applicationPackage);
    }

    @Override
    public byte[] getDev(ApplicationId application) {
        return requireNonNull(devStore.get(application));
    }

}
