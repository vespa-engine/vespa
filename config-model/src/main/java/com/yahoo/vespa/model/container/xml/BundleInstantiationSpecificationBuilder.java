// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.component.ComponentSpecification;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.List;

import static com.yahoo.vespa.model.container.xml.ContainerModelBuilder.SEARCH_HANDLER_CLASS;

/**
 * This object builds a bundle instantiation spec from an XML element.
 *
 * @author gjoranv
 */
public class BundleInstantiationSpecificationBuilder {

    public static BundleInstantiationSpecification build(Element spec) {
        ComponentSpecification id = XmlHelper.getIdRef(spec);
        ComponentSpecification classId = getComponentSpecification(spec, "class");
        ComponentSpecification bundle = getComponentSpecification(spec, "bundle");

        BundleInstantiationSpecification instSpec = new BundleInstantiationSpecification(id, classId, bundle);
        validate(instSpec);

        return bundle == null ? setBundleForKnownClass(instSpec) : instSpec;
    }

    private static BundleInstantiationSpecification setBundleForKnownClass(BundleInstantiationSpecification spec) {
        return BundleMapper.getBundle(spec.getClassName()).
                map(spec::inBundle).
                orElse(spec);
    }


    private static void validate(BundleInstantiationSpecification instSpec) {
        List<String> forbiddenClasses = Arrays.asList(
                SEARCH_HANDLER_CLASS,
                "com.yahoo.processing.handler.ProcessingHandler");

        for (String forbiddenClass: forbiddenClasses) {
            if (forbiddenClass.equals(instSpec.getClassName())) {
                throw new RuntimeException("Setting up " + forbiddenClass + " manually is not supported.");
            }
        }
    }

    //null if missing
    private static ComponentSpecification getComponentSpecification(Element spec, String attributeName) {
        return (spec.hasAttribute(attributeName)) ?
                new ComponentSpecification(spec.getAttribute(attributeName)) :
                null;
    }

}
