// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.component.ComponentId;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.xml.BundleInstantiationSpecificationBuilder;
import org.w3c.dom.Element;

/**
 * @author gjoranv
 * @author Tony Vaagenes
 */
public class DomComponentBuilder extends VespaDomBuilder.DomConfigProducerBuilder<Component> {

    public static final String elementName = "component" ;

    private final ComponentId namespace;

    public DomComponentBuilder() {
        this(null);
    }

    public DomComponentBuilder(ComponentId namespace) {
        this.namespace = namespace;
    }

    protected Component doBuild(AbstractConfigProducer ancestor, Element spec) {
        Component component = buildComponent(spec);
        addChildren(ancestor, spec, component);
        return component;
    }

    private Component buildComponent(Element spec) {
        BundleInstantiationSpecification bundleSpec =
                BundleInstantiationSpecificationBuilder.build(spec, false).nestInNamespace(namespace);

        return new Component<Component<?, ?>, ComponentModel>(new ComponentModel(bundleSpec));
    }

    public static void addChildren(AbstractConfigProducer ancestor, Element componentNode, Component<? super Component<?, ?>, ?> component) {
        for (Element childNode : XML.getChildren(componentNode, elementName)) {
            addAndInjectChild(ancestor, component, childNode);
        }
    }

    private static void addAndInjectChild(AbstractConfigProducer ancestor, Component<? super Component<?, ?>, ?> component, Element childNode) {
        Component<?, ?> child = new DomComponentBuilder(component.getComponentId()).build(ancestor, childNode);
        component.addComponent(child);
        component.inject(child);
    }

}
