// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import org.junit.Test;
import org.osgi.framework.Constants;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class FelixParamsTestCase {

    @Test
    public void requireThatAccessorsWork() {
        FelixParams params = new FelixParams();
        params.setCachePath("foo");
        assertEquals("foo", params.getCachePath());
        params.setLoggerEnabled(true);
        assertTrue(params.isLoggerEnabled());
    }

    @Test
    public void requireThatSystemPackagesAreNotReplaced() {
        FelixParams params = new FelixParams();
        Map<String, String> config = params.toConfig();
        assertNotNull(config);
        String str = config.get(Constants.FRAMEWORK_SYSTEMPACKAGES);
        assertNotNull(str);
        assertTrue(str.contains(ExportPackages.getSystemPackages()));

        params.exportPackage("foo");
        assertNotNull(config = params.toConfig());
        assertNotNull(str = config.get(Constants.FRAMEWORK_SYSTEMPACKAGES));
        assertTrue(str.contains(ExportPackages.getSystemPackages()));
        assertTrue(str.contains("foo"));
    }

    @Test
    public void requireThatExportsAreIncludedInConfig() {
        FelixParams params = new FelixParams();
        Map<String, String> config = params.toConfig();
        assertNotNull(config);
        String[] prev = config.get(Constants.FRAMEWORK_SYSTEMPACKAGES).split(",");

        params.exportPackage("foo");
        params.exportPackage("bar");
        assertNotNull(config = params.toConfig());
        String[] next = config.get(Constants.FRAMEWORK_SYSTEMPACKAGES).split(",");

        assertEquals(prev.length + 2, next.length);

        List<String> diff = new LinkedList<>();
        diff.addAll(Arrays.asList(next));
        diff.removeAll(Arrays.asList(prev));
        assertEquals(2, diff.size());
        assertTrue(diff.contains("foo"));
        assertTrue(diff.contains("bar"));
    }
}
