// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component.chain;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Phase;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.container.core.ChainsConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.yahoo.container.core.ChainsConfig.Chains.*;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 *
 * Generates config for a all the chains.
 */
class ChainsConfigGenerator<T extends Chain> {

    public static <T extends Chain> void generate(ChainsConfig.Builder builder, Collection<T> chains) {
        for (T chain : chains) {
            builder.chains(getChain(chain));
        }
     }

    private static <T extends Chain> ChainsConfig.Chains.Builder getChain(T chain) {
        ChainSpecification specification = chain.getChainSpecification();

        return new ChainsConfig.Chains.Builder()
                .type(chain.getType())
                .id(specification.componentId.stringValue())
                .components(getComponents(specification.componentReferences))
                .inherits(getComponents(specification.inheritance.chainSpecifications))
                .excludes(getComponents(specification.inheritance.excludedComponents))
                .phases(getPhases(specification.phases()));
    }

    private static List<String> getComponents(Collection<ComponentSpecification> componentSpecs) {
        List<String> components = new ArrayList<>();
        for (ComponentSpecification spec : componentSpecs)
            components.add(spec.stringValue());
        return components;
    }

    private static List<Phases.Builder> getPhases(Collection<Phase> phases) {
        List<Phases.Builder> builders = new ArrayList<>();
        for (Phase phase : phases) {
            builders.add(
                    new Phases.Builder()
                            .id(phase.getName())
                            .before(phase.before())
                            .after(phase.after()));
        }
        return builders;
    }

}
