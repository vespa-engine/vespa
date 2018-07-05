// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.HttpFilter;
import com.yahoo.vespa.model.container.xml.BundleInstantiationSpecificationBuilder;
import org.w3c.dom.Element;

/**
 * @author Tony Vaagenes
 */
public class DomFilterBuilder extends VespaDomBuilder.DomConfigProducerBuilder<Component> {
    @Override
    protected Component doBuild(AbstractConfigProducer ancestor, Element element) {
        return new HttpFilter(BundleInstantiationSpecificationBuilder.build(element, false));
    }
}
