// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.test.TestDriver;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * @author Simon Thoresen Hult
 */
public class ExportPackagesIntegrationTest {

    @Test
    public void requireThatManifestContainsExportPackage() throws BundleException {
        FelixFramework felix = TestDriver.newOsgiFramework();
        felix.start();
        List<Bundle> bundles = felix.installBundle("jdisc_core.jar");
        assertEquals(1, bundles.size());
        Object obj = bundles.get(0).getHeaders().get("Export-Package");
        assertTrue(obj instanceof String);
        String str = (String)obj;
        assertTrue(str.contains(ExportPackages.getSystemPackages()));
        assertTrue(str.contains("com.yahoo.jdisc"));
        assertTrue(str.contains("com.yahoo.jdisc.application"));
        assertTrue(str.contains("com.yahoo.jdisc.handler"));
        assertTrue(str.contains("com.yahoo.jdisc.service"));
        felix.stop();
    }
}
