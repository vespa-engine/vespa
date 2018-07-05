// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.config.model.builder.xml.XmlHelper;
import org.w3c.dom.Element;

/**
 * reads the common attributes and elements of all chained component elements.
 * @author Tony Vaagenes
 */
public abstract class GenericChainedComponentModelBuilder {
    //The componentId might be used as a spec later(for example as class or
    //bundle), so we must treat it as a specification until then.
    protected final ComponentSpecification componentId;
    protected final Dependencies dependencies;

    public GenericChainedComponentModelBuilder(Element spec) {
        componentId = readComponentId(spec);
        dependencies = readDependencies(spec);
    }

    private Dependencies readDependencies(Element spec) {
        return new DependenciesBuilder(spec).build();
    }

    protected ComponentSpecification readComponentId(Element spec) {
        return XmlHelper.getIdRef(spec);
    }

    protected abstract ChainedComponentModel build();

}
