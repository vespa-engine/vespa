// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.vespa.model.container.xml.BundleInstantiationSpecificationBuilder;
import org.w3c.dom.Element;

/**
 * Builds a regular ChainedComponentModel from an element.
 * @author Tony Vaagenes
 */
public class ChainedComponentModelBuilder extends GenericChainedComponentModelBuilder {
    protected final BundleInstantiationSpecification bundleInstantiationSpec;

    public ChainedComponentModelBuilder(Element spec) {
        super(spec);
        bundleInstantiationSpec = BundleInstantiationSpecificationBuilder.build(spec);
    }

    public ChainedComponentModel build() {
        return new ChainedComponentModel(bundleInstantiationSpec, dependencies);
    }

}
