// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.xml.BundleInstantiationSpecificationBuilder;
import org.w3c.dom.Element;

/**
 * @author gjoranv
 * @author Tony Vaagenes
 */
public class DomComponentBuilder extends VespaDomBuilder.DomConfigProducerBuilder<Component<?, ?>, AnyConfigProducer> {

    public static final String elementName = "component" ;

    private final ComponentId namespace;

    public DomComponentBuilder() {
        this(null);
    }

    public DomComponentBuilder(ComponentId namespace) {
        this.namespace = namespace;
    }

    @Override
    protected Component doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, Element spec) {
        Component component = buildComponent(spec);
        addChildren(deployState, ancestor, spec, component);
        return component;
    }

    private Component buildComponent(Element spec) {
        BundleInstantiationSpecification bundleSpec =
                BundleInstantiationSpecificationBuilder.build(spec).nestInNamespace(namespace);

        return new Component<Component<?, ?>, ComponentModel>(new ComponentModel(bundleSpec));
    }

    public static void addChildren(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, Element componentNode, Component<? super Component<?, ?>, ?> component) {
        for (Element childNode : XML.getChildren(componentNode, elementName)) {
            addAndInjectChild(deployState, ancestor, component, childNode);
        }
    }

    private static void addAndInjectChild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, Component<? super Component<?, ?>, ?> component, Element childNode) {
        Component<?, ?> child = new DomComponentBuilder(component.getComponentId()).build(deployState, ancestor, childNode);
        component.addComponent(child);
        component.inject(child);
    }

}
