// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationStore;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;

import java.util.HashMap;
import java.util.Map;

public class ApplicationStoreMock implements ApplicationStore {

    Map<String, byte[]> store = new HashMap<>();

    @Override
    public byte[] getApplicationPackage(ApplicationId application, ApplicationVersion applicationVersion) {
        return store.get(path(application, applicationVersion));
    }

    @Override
    public void putApplicationPackage(ApplicationId application, ApplicationVersion applicationVersion, byte[] applicationPackage) {
        store.put(path(application, applicationVersion), applicationPackage);
    }

    @Override
    public void putTesterPackage(ApplicationId tester, ApplicationVersion applicationVersion, byte[] testerPackage) {
        store.put(path(tester, applicationVersion), testerPackage);
    }

    @Override
    public byte[] getTesterPackage(ApplicationId tester, ApplicationVersion applicationVersion) {
        return store.get(path(tester, applicationVersion));
    }

    String path(ApplicationId tester, ApplicationVersion applicationVersion) {
        return tester.toString() + applicationVersion.id();
    }

}
