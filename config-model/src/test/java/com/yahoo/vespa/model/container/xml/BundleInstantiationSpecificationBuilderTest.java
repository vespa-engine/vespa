// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.search.grouping.GroupingValidator;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author gjoranv
 * @author ollivir
 */

public class BundleInstantiationSpecificationBuilderTest {

    @Test
    public void bundle_is_not_replaced_for_user_defined_class() throws IOException, SAXException {
        final String userDefinedClass = "my own class that will also be set as bundle";
        verifyExpectedBundle(userDefinedClass, null, userDefinedClass);
    }

    @Test
    public void bundle_is_replaced_for_internal_class() throws IOException, SAXException {
        String internalClass = GroupingValidator.class.getName();
        verifyExpectedBundle(internalClass, null, BundleMapper.searchAndDocprocBundle);
    }

    @Test
    public void bundle_is_not_replaced_for_internal_class_with_explicitly_set_bundle()
            throws IOException, SAXException {
        String internalClass = GroupingValidator.class.getName();
        String explicitBundle = "my-own-implementation";
        verifyExpectedBundle(internalClass, explicitBundle, explicitBundle);
    }

    private static void verifyExpectedBundle(String className, String explicitBundle, String expectedBundle)
            throws IOException, SAXException {
        String xml = "<component id=\"_\" class=\"" + className + "\"";
        if (explicitBundle != null) {
            xml += " bundle=\"" + explicitBundle + "\"";
        }
        xml += " />";
        InputStream xmlStream = IOUtils.toInputStream(xml);
        Element component = XmlHelper.getDocumentBuilder().parse(xmlStream).getDocumentElement();

        BundleInstantiationSpecification spec = BundleInstantiationSpecificationBuilder.build(component, false);
        assertThat(spec.bundle, is(ComponentSpecification.fromString(expectedBundle)));
    }
}
