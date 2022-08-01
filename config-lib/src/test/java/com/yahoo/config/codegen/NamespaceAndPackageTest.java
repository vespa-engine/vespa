// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import com.github.myproject.NamespaceAndPackageConfig;
import com.github.myproject.PackageConfig;
import com.yahoo.my.namespace.NamespaceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author gjoranv
 */
public class NamespaceAndPackageTest {
    private static String NAMESPACE = "my.namespace";
    private static String PACKAGE = "com.github.myproject";

    @Test
    void namespace_is_set_from_def_file() {
        assertEquals(NAMESPACE, NamespaceConfig.CONFIG_DEF_NAMESPACE);
    }

    @Test
    void package_is_used_as_namespace_when_namespace_is_not_set_explicitly() {
        assertEquals(PACKAGE, PackageConfig.CONFIG_DEF_NAMESPACE);
    }

    @Test
    void package_does_not_override_namespace() {
        assertEquals(NAMESPACE, NamespaceAndPackageConfig.CONFIG_DEF_NAMESPACE);

    }
}
