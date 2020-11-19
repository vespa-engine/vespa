// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.osgi;

import com.yahoo.component.ComponentSpecification;
import org.osgi.framework.Bundle;

import java.util.Collection;
import java.util.List;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public interface Osgi {

    Bundle[] getBundles();

    /** Returns all bundles that have not been scheduled for uninstall. */
    List<Bundle> getCurrentBundles();

    Bundle getBundle(ComponentSpecification bundleId);

    List<Bundle> install(String absolutePath);

    void allowDuplicateBundles(Collection<Bundle> bundles);

    default boolean hasFelixFramework() {
       return false;
    }

}
