// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config;

import com.yahoo.osgi.MockOsgi;
import org.osgi.framework.Bundle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author gjoranv
 */
class TestOsgi extends MockOsgi {

    private final Map<String, Bundle> availableBundles;

    private final List<Bundle> installedBundles = new ArrayList<>();
    private final List<Bundle> allowedDuplicates = new ArrayList<>();

    TestOsgi(Map<String, Bundle> availableBundles) {
        this.availableBundles = availableBundles;
    }

    @Override
    public List<Bundle> install(String fileReferenceValue) {
        if (! availableBundles.containsKey(fileReferenceValue))
            throw new IllegalArgumentException("No such bundle: " + fileReferenceValue);

        Bundle bundle = availableBundles.get(fileReferenceValue);
        installedBundles.add(bundle);
        return List.of(bundle);
    }

    @Override
    public Bundle[] getBundles() {
        return installedBundles.toArray(new Bundle[0]);
    }

    public List<Bundle> getInstalledBundles() {
        return installedBundles;
    }

    @Override
    public List<Bundle> getCurrentBundles() {
        var currentBundles = new ArrayList<>(installedBundles);
        currentBundles.removeAll(allowedDuplicates);
        return currentBundles;
    }

    @Override
    public void allowDuplicateBundles(Collection<Bundle> bundles) {
        allowedDuplicates.addAll(bundles);
    }

}
