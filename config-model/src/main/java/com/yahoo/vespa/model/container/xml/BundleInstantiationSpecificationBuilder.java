// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.vespa.model.container.PlatformBundles;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.List;

import static com.yahoo.vespa.model.container.component.chain.ProcessingHandler.PROCESSING_HANDLER_CLASS;

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

        return bundle == null ? setBundleForSearchAndDocprocComponents(instSpec) : instSpec;
    }

    private static BundleInstantiationSpecification setBundleForSearchAndDocprocComponents(BundleInstantiationSpecification spec) {
        if (PlatformBundles.isSearchAndDocprocClass(spec.getClassName()))
            return spec.inBundle(PlatformBundles.SEARCH_AND_DOCPROC_BUNDLE);
        else
            return spec;
    }


    private static void validate(BundleInstantiationSpecification instSpec) {
        List<String> forbiddenClasses = Arrays.asList(
                SearchHandler.HANDLER_CLASS,
                PROCESSING_HANDLER_CLASS);

        for (String forbiddenClass: forbiddenClasses) {
            if (forbiddenClass.equals(instSpec.getClassName())) {
                throw new IllegalArgumentException("Setting up " + forbiddenClass + " manually is not supported");
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
