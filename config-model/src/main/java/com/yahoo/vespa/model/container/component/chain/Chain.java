// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component.chain;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.container.component.ComponentGroup;

import java.util.ArrayList;
import java.util.Collection;

import static com.yahoo.container.core.ChainsConfig.Chains.Type;

/**
 * Represents a component chain in the vespa model.
 * The inner components are represented as children.
 *
 * @author Tony Vaagenes
 * @author gjoranv
 */
public class Chain<T extends ChainedComponent<?>> extends TreeConfigProducer<AnyConfigProducer> {

    private final ComponentId componentId;
    private final ChainSpecification specWithoutInnerComponents;
    private final ComponentGroup<T> innerComponentsGroup;
    private static final Type.Enum TYPE = Type.SEARCH;

    public Chain(ChainSpecification specWithoutInnerComponents) {
        super(specWithoutInnerComponents.componentId.stringValue());

        this.componentId = specWithoutInnerComponents.componentId;
        this.specWithoutInnerComponents = specWithoutInnerComponents;
        assertNoInnerComponents(specWithoutInnerComponents);

        innerComponentsGroup = new ComponentGroup<>(this, "component");
    }

    private void assertNoInnerComponents(ChainSpecification specWithoutInnerComponents) {
        for (ComponentSpecification component : specWithoutInnerComponents.componentReferences) {
            assert (component.getNamespace() == null);
        }
    }

    public void addInnerComponent(T component) {
        innerComponentsGroup.addComponent(component);
    }

    public ChainSpecification getChainSpecification() {
        Collection<ComponentSpecification> innerComponentSpecifications = new ArrayList<>();

        for (ChainedComponent innerComponent : getInnerComponents()) {
            innerComponentSpecifications.add(innerComponent.getGlobalComponentId().toSpecification());
        }

        return specWithoutInnerComponents.
                addComponents(innerComponentSpecifications).
                setComponentId(getGlobalComponentId());
    }

    public Collection<T> getInnerComponents() {
       return innerComponentsGroup.getComponents();
    }

    public ComponentId getGlobalComponentId() {
        return componentId;
    }

    public final ComponentId getId() { return getGlobalComponentId(); }

    public final ComponentId getComponentId() {
        return componentId;
    }

    // TODO: remove when DocumentProcessingHandler takes its own version of the chains config as ctor arg
    public Type.Enum getType() {
        return TYPE;
    }

    @Override
    public String toString() {
        return "chain '" + componentId + "'";
    }

}
