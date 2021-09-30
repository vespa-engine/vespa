// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.model;

import java.util.*;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;

import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.Phase;
import com.yahoo.container.core.ChainsConfig;

/**
 * Builds a chains model from config.
 *
 * @author Tony Vaagenes
 */
public class ChainsModelBuilder {

    public static ChainsModel buildFromConfig(ChainsConfig chainsConfig) {
        ChainsModel model = createChainsModel(chainsConfig);

         for (ChainsConfig.Components component : chainsConfig.components()) {
             ChainedComponentModel componentModel = createChainedComponentModel(component);
             model.register(componentModel.getComponentId(), componentModel);
         }
         return model;
    }

    private static ChainedComponentModel createChainedComponentModel(ChainsConfig.Components component) {
        return new ChainedComponentModel(
                new BundleInstantiationSpecification(new ComponentSpecification(component.id()), null, null),
                createDependencies(
                        component.dependencies().provides(),
                        component.dependencies().before(),
                        component.dependencies().after()),
                null);
    }

    private static ChainsModel createChainsModel(ChainsConfig chainsConfig) {
        ChainsModel model = new ChainsModel();
        for (ChainsConfig.Chains chainConfig : chainsConfig.chains()) {
            model.register(
                    createChainSpecification(chainConfig));
        }
        return model;
    }

    private static ChainSpecification createChainSpecification(ChainsConfig.Chains config) {
        return new ChainSpecification(new ComponentId(config.id()),
                createInheritance(config.inherits(), config.excludes()),
                createPhases(config.phases()),
                createComponentSpecifications(config.components()));
    }

    private static Collection<Phase> createPhases(List<ChainsConfig.Chains.Phases> phases) {
        Collection<Phase> result = new ArrayList<>();
        for (ChainsConfig.Chains.Phases phase : phases) {
            result.add(
                    new Phase(phase.id(), createDependencies(null, phase.before(), phase.after())));
        }
        return result;
    }

    private static Dependencies createDependencies(List<String> provides,
                                                   List<String> before, List<String> after) {
        return new Dependencies(provides, before, after);
    }

    private static Set<ComponentSpecification> createComponentSpecifications(List<String> stringSpecs) {
        Set<ComponentSpecification> specifications = new LinkedHashSet<>();
        for (String stringSpec : stringSpecs) {
            specifications.add(new ComponentSpecification(stringSpec));
        }
        return specifications;
    }

    private static ChainSpecification.Inheritance createInheritance(List<String> inherit, List<String> exclude) {
        return new ChainSpecification.Inheritance(
                createComponentSpecifications(inherit),
                createComponentSpecifications(exclude));
    }
}
