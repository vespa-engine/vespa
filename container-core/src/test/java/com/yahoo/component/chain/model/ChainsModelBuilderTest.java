// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.model;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.container.core.ChainsConfig;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static com.yahoo.container.core.ChainsConfig.Components;
import static com.yahoo.container.core.ChainsConfig.Chains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class ChainsModelBuilderTest {

    @Test
    public void components_are_added_to_componentModels() throws Exception {
        ChainsModel model = chainsModel();
        assertEquals(2, model.allComponents().size());
        assertTrue(model.componentModels().containsKey(new ComponentId("componentA")));
    }

    @Test
    public void components_are_added_to_chainSpecification() throws Exception {
        ChainsModel model = chainsModel();
        ChainSpecification chainSpec = model.chainSpecifications().get(new ComponentId("chain1")).model();
        assertTrue(getComponentsByName(chainSpec.componentReferences).containsKey("componentA"));
    }

    @Test
    public void inherited_chains_are_added_to_chainSpecification() throws Exception {
        ChainsModel model = chainsModel();
        ChainSpecification inheritsChain1 = model.chainSpecifications().get(new ComponentId("inheritsChain1")).model();
        assertEquals(2, model.allChainsFlattened().size());
        assertTrue(getComponentsByName(inheritsChain1.inheritance.chainSpecifications).containsKey("chain1"));
        assertTrue(getComponentsByName(inheritsChain1.inheritance.excludedComponents).containsKey("componentA"));
     }

    private ChainsModel chainsModel() {
        ChainsConfig.Builder builder = new ChainsConfig.Builder()
                .components(new Components.Builder()
                        .id("componentA"))
                .components(new Components.Builder()
                        .id("componentB"))
                .chains(new Chains.Builder()
                        .id("chain1")
                        .components("componentA")
                        .components("componentB"))
                .chains(new Chains.Builder()
                        .id("inheritsChain1")
                        .inherits("chain1")
                        .excludes("componentA"));
        ChainsConfig config = new ChainsConfig(builder);

        ChainsModel model = ChainsModelBuilder.buildFromConfig(config);
        model.validate();
        return model;
    }

    private static Map<String, ComponentSpecification>
    getComponentsByName(Set<ComponentSpecification> componentSpecifications) {
        return ChainSpecification.componentsByName(componentSpecifications);
    }

}
