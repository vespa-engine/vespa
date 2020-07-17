// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.osgi;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.jdisc.test.NonWorkingOsgiFramework;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;

import java.util.Collections;
import java.util.List;

/**
 * @author Tony Vaagenes
 */
public class MockOsgi extends NonWorkingOsgiFramework implements Osgi {

    @Override
    public List<Bundle> getInitialBundles() {
        return Collections.emptyList();
    }

    @Override
    public Bundle[] getBundles() {
        return new Bundle[0];
    }

    @Override
    public List<Bundle> getCurrentBundles() {
        return Collections.emptyList();
    }

    @Override
    public Bundle getBundle(ComponentSpecification bundleId) {
        return null;
    }

    @Override
    public List<Bundle> install(String absolutePath) {
        return Collections.emptyList();
    }

}
