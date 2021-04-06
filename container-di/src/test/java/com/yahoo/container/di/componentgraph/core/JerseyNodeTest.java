// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core;

import com.yahoo.container.bundle.MockBundle;
import com.yahoo.container.di.config.RestApiContext;
import com.yahoo.container.di.osgi.OsgiUtil;
import org.junit.Test;
import org.osgi.framework.wiring.BundleWiring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;

/**
 * @author gjoranv
 * @author ollivir
 */

public class JerseyNodeTest {
    private MockBundle bundle;
    private List<String> bundleClasses;
    private final Map<String, String> resources;

    public JerseyNodeTest() {
        resources = new HashMap<>();
        resources.put("com/foo", "com/foo/Foo.class");
        resources.put("com/bar", "com/bar/Bar.class");
        bundle = new MockBundle() {
            @Override
            public Collection<String> listResources(String path, String ignored, int options) {
                if ((options & BundleWiring.LISTRESOURCES_RECURSE) != 0 && path.equals("/")) {
                    return resources.values();
                } else {
                    return Collections.singleton(resources.get(path));
                }
            }
        };
        bundleClasses = new ArrayList<>(resources.values());
    }

    @Test
    public void all_bundle_entries_are_returned_when_no_packages_are_given() {
        Collection<String> entries = OsgiUtil.getClassEntriesInBundleClassPath(bundle, Collections.emptySet());
        assertThat(entries, containsInAnyOrder(bundleClasses.toArray()));
    }

    @Test
    public void only_bundle_entries_from_the_given_packages_are_returned() {
        Collection<String> entries = OsgiUtil.getClassEntriesInBundleClassPath(bundle, Collections.singleton("com.foo"));
        assertThat(entries, contains(resources.get("com/foo")));
    }

    @Test
    public void bundle_info_is_initialized() {
        RestApiContext.BundleInfo bundleInfo = JerseyNode.createBundleInfo(bundle, Collections.emptyList());
        assertThat(bundleInfo.symbolicName, is(bundle.getSymbolicName()));
        assertThat(bundleInfo.version, is(bundle.getVersion()));
        assertThat(bundleInfo.fileLocation, is(bundle.getLocation()));
    }
}
