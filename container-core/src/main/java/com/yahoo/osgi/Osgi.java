// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.osgi;

import com.yahoo.component.ComponentSpecification;
import org.osgi.framework.Bundle;

import java.util.List;

/**
 * @author Tony Vaagenes
 */
public interface Osgi {

    Bundle[] getBundles();

    Bundle getBundle(ComponentSpecification bundleId);

    List<Bundle> install(String absolutePath);

    void uninstall(Bundle bundle);

    void refreshPackages();

}
