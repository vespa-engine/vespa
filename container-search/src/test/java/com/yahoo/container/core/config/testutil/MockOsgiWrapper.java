// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config.testutil;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.osgi.OsgiWrapper;
import org.osgi.framework.Bundle;

import java.util.Collection;
import java.util.List;

/**
 * @author gjoranv
 */
public class MockOsgiWrapper implements OsgiWrapper {

    @Override
    public Bundle[] getBundles() {
        return new Bundle[0];
    }

    @Override
    public List<Bundle> getCurrentBundles() {
        return List.of();
    }

    @Override
    public Bundle getBundle(ComponentSpecification bundleId) {
        return null;
    }

    @Override
    public List<Bundle> install(String absolutePath) {
        return List.of();
    }

    @Override
    public void allowDuplicateBundles(Collection<Bundle> bundles) {  }

}
