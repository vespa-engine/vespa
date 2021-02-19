// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.component.chain.model.ChainsModel;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.ConfigurationRuntimeException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Configures a registry of chains.
 *
 * @author bratseth
 * @author gjoranv
 */
public class ChainsConfigurer {

    public static <COMPONENT extends ChainedComponent> void prepareChainRegistry(
            ComponentRegistry<Chain<COMPONENT>> registry,
            ChainsModel model,
            ComponentRegistry<COMPONENT> allComponents) {

        initDependencies(model, allComponents);
        instantiateChains(registry, model, allComponents);
    }

    private static <COMPONENT extends ChainedComponent> void initDependencies(
            ChainsModel model,
            ComponentRegistry<COMPONENT> allComponents) {

        for (ChainedComponentModel componentModel : model.allComponents()) {
            COMPONENT component = getComponentOrThrow(allComponents, componentModel.getComponentId().toSpecification());
            component.initDependencies(componentModel.dependencies);
        }
    }

    private static <COMPONENT extends ChainedComponent> COMPONENT getComponentOrThrow(
            ComponentRegistry<COMPONENT> registry,
            ComponentSpecification specification) {

        COMPONENT component = registry.getComponent(specification);
        if (component == null) {
            throw new ConfigurationRuntimeException("No such component '" + specification + "'");
        }

        return component;
    }

    private static <COMPONENT extends ChainedComponent> void instantiateChains(
            ComponentRegistry<Chain<COMPONENT>> chainRegistry,
            ChainsModel model,
            ComponentRegistry<COMPONENT> allComponents) {

        for (ChainSpecification chain : model.allChainsFlattened()) {
            try {
                Chain<COMPONENT> componentChain = new Chain<>(chain.componentId,
                        resolveComponents(chain.componentReferences, allComponents),
                        chain.phases());
                chainRegistry.register(chain.componentId, componentChain);
            } catch (Exception e) {
                throw new ConfigurationRuntimeException("Invalid chain '" + chain.componentId + "'", e);
            }
        }
    }

    private static <T extends ChainedComponent> List<T> resolveComponents(
            Set<ComponentSpecification> componentSpecifications,
            ComponentRegistry<T> allComponents) {

        List<T> components = new ArrayList<>(componentSpecifications.size());
        for (ComponentSpecification componentSpec : componentSpecifications) {
            T component = getComponentOrThrow(allComponents, componentSpec);
            components.add(component);
        }
        return components;
    }

}
