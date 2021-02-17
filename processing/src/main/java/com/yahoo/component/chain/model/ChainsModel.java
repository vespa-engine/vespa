// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.provider.ComponentRegistry;

/**
 * A model of how the chains and components should be created.
 *
 * @author Tony Vaagenes
 */
public class ChainsModel {

    private final ComponentRegistry<ComponentAdaptor<ChainSpecification>> chainSpecifications = new ComponentRegistry<>();
    private final ComponentRegistry<ComponentAdaptor<ChainedComponentModel>> componentModels = new ComponentRegistry<>();

    public void register(ChainSpecification chainSpecification) {
        chainSpecifications.register(chainSpecification.componentId,
                ComponentAdaptor.create(chainSpecification.componentId, chainSpecification));
    }

    public void register(ComponentId globalComponentId, ChainedComponentModel componentModel) {
        assert (componentModel.getComponentId().withoutNamespace().equals(
                globalComponentId.withoutNamespace()));

        componentModels.register(globalComponentId, ComponentAdaptor.create(globalComponentId, componentModel));
    }

    public Collection<ChainedComponentModel> allComponents() {
        Collection<ChainedComponentModel> components = new ArrayList<>();
        for (ComponentAdaptor<ChainedComponentModel> component : componentModels.allComponents()) {
            components.add(component.model);
        }
        return components;
    }

    public Collection<ChainSpecification> allChainsFlattened() {
        Resolver<ChainSpecification> resolver = new Resolver<ChainSpecification>() {
            @Override
            public ChainSpecification resolve(ComponentSpecification componentSpecification) {
                ComponentAdaptor<ChainSpecification> spec = chainSpecifications.getComponent(componentSpecification);
                return (spec==null) ? null : spec.model;
            }
        };

        Collection<ChainSpecification> chains = new ArrayList<>();
        for (ComponentAdaptor<ChainSpecification> chain : chainSpecifications.allComponents()) {
            chains.add(chain.model.flatten(resolver));
        }
        return chains;
    }

    public void validate() {
        allChainsFlattened();
        for (ComponentAdaptor<ChainSpecification> chain : chainSpecifications.allComponents()) {
            validate(chain.model);
        }
    }

    private void validate(ChainSpecification model) {
        for (ComponentSpecification componentSpec : model.componentReferences) {
            if (componentModels.getComponent(componentSpec) == null) {
                throw new RuntimeException("No component matching the component specification " + componentSpec);
            }
        }
    }

    // For testing
    Map<ComponentId, ComponentAdaptor<ChainSpecification>> chainSpecifications() {
        return chainSpecifications.allComponentsById();
    }

    // For testing
    Map<ComponentId, ComponentAdaptor<ChainedComponentModel>> componentModels() {
        return componentModels.allComponentsById();
    }

}
