// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config;

import com.yahoo.config.FileReference;
import com.yahoo.filedistribution.fileacquirer.MockFileAcquirer;
import com.yahoo.osgi.MockOsgi;
import org.osgi.framework.Bundle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author gjoranv
 */
public class TestOsgi extends MockOsgi implements com.yahoo.container.di.Osgi {

    private final ApplicationBundleLoader bundleLoader;
    private final Map<String, Bundle> availableBundles;

    private final List<Bundle> installedBundles = new ArrayList<>();
    private final List<Bundle> allowedDuplicates = new ArrayList<>();

    public TestOsgi(Map<String, Bundle> availableBundles) {
        this.availableBundles = availableBundles;

        var bundleInstaller = new BundleTestUtil.TestBundleInstaller(MockFileAcquirer.returnFile(null));
        bundleLoader = new ApplicationBundleLoader(this, bundleInstaller);
    }

    public ApplicationBundleLoader bundleLoader() { return bundleLoader; }

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
        allowedDuplicates.clear();
        allowedDuplicates.addAll(bundles);
    }

    @Override
    public void useApplicationBundles(Collection<FileReference> bundles, long generation) {
        bundleLoader.useBundles(new ArrayList<>(bundles));
    }

    @Override
    public Set<Bundle> completeBundleGeneration(GenerationStatus status) {
        return bundleLoader.completeGeneration(status);
    }

    public void removeBundle(Bundle bundle) {
        installedBundles.remove(bundle);
    }
}
