// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.osgi.provider.model;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author gjoranv
 */
public class ComponentModelTest {

    @Test
    void create_from_instantiation_spec() {
        ComponentModel model = new ComponentModel(
                BundleInstantiationSpecification.fromStrings("id", "class", "bundle"));
        verifyBundleSpec(model);
    }

    @Test
    void require_exception_upon_null_instantiation_spec() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> {
            ComponentModel model = new ComponentModel(null);
        });
    }

    @Test
    void create_from_instantiation_spec_and_config_id() throws Exception {
        ComponentModel model = new ComponentModel(
                BundleInstantiationSpecification.fromStrings("id", "class", "bundle"), "configId");
        verifyBundleSpec(model);
        assertEquals("configId", model.configId);
    }

    @Test
    void create_from_strings() throws Exception {
        ComponentModel model = new ComponentModel("id", "class", "bundle", "configId");
        verifyBundleSpec(model);
        assertEquals("configId", model.configId);
    }

    private void verifyBundleSpec(ComponentModel model) {
         assertEquals("id", model.getComponentId().stringValue());
         assertEquals("class", model.getClassId().stringValue());
         assertEquals("bundle", model.bundleInstantiationSpec.bundle.stringValue());
     }
}
