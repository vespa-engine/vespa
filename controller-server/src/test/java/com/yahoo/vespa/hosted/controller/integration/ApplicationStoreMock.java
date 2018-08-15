package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationStore;

import java.util.HashMap;
import java.util.Map;

public class ApplicationStoreMock implements ApplicationStore {

    Map<String, byte[]> store = new HashMap<>();

    @Override
    public byte[] getApplicationPackage(ApplicationId application, String applicationVersion) {
        return store.get(path(application, applicationVersion));
    }

    @Override
    public void putApplicationPackage(ApplicationId application, String applicationVersion, byte[] applicationPackage) {
        store.put(path(application, applicationVersion), applicationPackage);
    }

    @Override
    public void putTesterPackage(ApplicationId tester, String applicationVersion, byte[] testerPackage) {
        store.put(path(tester, applicationVersion), testerPackage);
    }

    @Override
    public byte[] getTesterPackage(ApplicationId tester, String applicationVersion) {
        return store.get(path(tester, applicationVersion));
    }

    String path(ApplicationId tester, String applicationVersion) {
        return tester.toString() + applicationVersion;
    }
}
