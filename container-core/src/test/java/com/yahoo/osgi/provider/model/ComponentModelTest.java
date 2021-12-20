// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.osgi.provider.model;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author gjoranv
 */
public class ComponentModelTest {

    @Test
    public void create_from_instantiation_spec() {
        ComponentModel model = new ComponentModel(
                BundleInstantiationSpecification.getFromStrings("id", "class", "bundle"));
        verifyBundleSpec(model);
    }

    @Test(expected = IllegalArgumentException.class)
    public void require_exception_upon_null_instantiation_spec() throws Exception {
        ComponentModel model = new ComponentModel(null);
    }

    @Test
    public void create_from_instantiation_spec_and_config_id() throws Exception {
        ComponentModel model = new ComponentModel(
                BundleInstantiationSpecification.getFromStrings("id", "class", "bundle"), "configId");
        verifyBundleSpec(model);
        assertEquals("configId", model.configId);
    }

    @Test
    public void create_from_strings() throws Exception {
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
