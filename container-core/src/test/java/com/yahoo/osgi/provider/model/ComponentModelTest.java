// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.osgi.provider.model;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author gjoranv
 */
public class ComponentModelTest {

    @Test
    public void create_from_instantiation_spec() throws Exception {
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
        assertThat(model.configId, is("configId"));
    }

    @Test
    public void create_from_strings() throws Exception {
        ComponentModel model = new ComponentModel("id", "class", "bundle", "configId");
        verifyBundleSpec(model);
        assertThat(model.configId, is("configId"));
    }

    private void verifyBundleSpec(ComponentModel model) {
         assertThat(model.getComponentId().stringValue(), is("id"));
         assertThat(model.getClassId().stringValue(), is("class"));
         assertThat(model.bundleInstantiationSpec.bundle.stringValue(), is("bundle"));
     }
}
