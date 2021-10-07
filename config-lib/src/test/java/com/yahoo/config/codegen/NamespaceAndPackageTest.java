// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import com.github.myproject.NamespaceAndPackageConfig;
import com.github.myproject.PackageConfig;
import com.yahoo.my.namespace.NamespaceConfig;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author gjoranv
 */
public class NamespaceAndPackageTest {
    private static String NAMESPACE = "my.namespace";
    private static String PACKAGE = "com.github.myproject";

    @Test
    public void namespace_is_set_from_def_file() {
        assertThat(NamespaceConfig.CONFIG_DEF_NAMESPACE, is(NAMESPACE));
    }

    @Test
    public void package_is_used_as_namespace_when_namespace_is_not_set_explicitly() {
        assertThat(PackageConfig.CONFIG_DEF_NAMESPACE, is(PACKAGE));
    }

    @Test
    public void package_does_not_override_namespace() {
        assertThat(NamespaceAndPackageConfig.CONFIG_DEF_NAMESPACE, is(NAMESPACE));

    }
}
