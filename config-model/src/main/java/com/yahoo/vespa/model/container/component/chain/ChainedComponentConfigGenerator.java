// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component.chain;

import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.container.core.ChainsConfig;

import java.util.Set;

import static com.yahoo.container.core.ChainsConfig.Components;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 *
 * Generates config for all the chained components.
 */
class ChainedComponentConfigGenerator {

    public static void generate(ChainsConfig.Builder builder, Set<? extends ChainedComponent> components) {
         for (ChainedComponent<ChainedComponentModel> component : components) {
             builder.components(getComponent(component));
         }
     }

    private static Components.Builder getComponent(ChainedComponent<ChainedComponentModel> component) {
        return new Components.Builder()
                .id(component.getGlobalComponentId().stringValue())
                .dependencies(getDependencies(component));
    }

    private static Components.Dependencies.Builder getDependencies(ChainedComponent<ChainedComponentModel> component) {
        Dependencies dependencies = component.model.dependencies;
        return new Components.Dependencies.Builder()
                .provides(dependencies.provides())
                .before(dependencies.before())
                .after(dependencies.after());
    }

}
