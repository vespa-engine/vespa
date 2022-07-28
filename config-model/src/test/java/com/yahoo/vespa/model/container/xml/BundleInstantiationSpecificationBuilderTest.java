// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.search.grouping.GroupingValidator;
import com.yahoo.vespa.model.container.PlatformBundles;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author gjoranv
 * @author ollivir
 */

public class BundleInstantiationSpecificationBuilderTest {

    @Test
    void bundle_is_not_replaced_for_user_defined_class() {
        final String userDefinedClass = "my own class that will also be set as bundle";
        verifyExpectedBundle(userDefinedClass, null, userDefinedClass);
    }

    @Test
    void bundle_is_replaced_for_internal_class() {
        String internalClass = GroupingValidator.class.getName();
        verifyExpectedBundle(internalClass, null, PlatformBundles.SEARCH_AND_DOCPROC_BUNDLE);
    }

    @Test
    void bundle_is_not_replaced_for_internal_class_with_explicitly_set_bundle() {
        String internalClass = GroupingValidator.class.getName();
        String explicitBundle = "my-own-implementation";
        verifyExpectedBundle(internalClass, explicitBundle, explicitBundle);
    }

    private static void verifyExpectedBundle(String className, String explicitBundle, String expectedBundle) {
        String xml = "<component id=\"_\" class=\"" + className + "\"";
        if (explicitBundle != null) {
            xml += " bundle=\"" + explicitBundle + "\"";
        }
        xml += " />";
        Element component = XmlHelper.getDocument(new StringReader(xml)).getDocumentElement();

        BundleInstantiationSpecification spec = BundleInstantiationSpecificationBuilder.build(component);
        assertEquals(ComponentSpecification.fromString(expectedBundle), spec.bundle);
    }
}
