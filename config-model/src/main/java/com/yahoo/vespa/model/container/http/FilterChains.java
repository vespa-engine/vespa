// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import com.yahoo.vespa.model.container.component.chain.Chain;
import com.yahoo.vespa.model.container.component.chain.Chains;

import java.util.Set;

/**
 * @author Tony Vaagenes
 */
public class FilterChains extends Chains<Chain<Filter>>  {

    public FilterChains(AbstractConfigProducer parent) {
        super(parent, "filters");

        addChild(new SimpleComponent("com.yahoo.container.http.filter.FilterChainRepository"));
    }

    public boolean hasChain(ComponentId filterChain) {
        for (Chain<Filter> chain : allChains().allComponents()) {
            if (chain.getId().equals(filterChain))
                return true;
        }
        return false;
    }

    public boolean hasChainThatInherits(ComponentId filterChain) {
        for (Chain<Filter> chain : allChains().allComponents()) {
            for (ComponentSpecification spec : chain.getChainSpecification().inheritance.chainSpecifications) {
                if(spec.toId().equals(filterChain))
                    return true;
            }
        }
        return false;
    }

    public static ChainSpecification emptyChainSpec(ComponentId chainId) {
        return new ChainSpecification(chainId,
                                      new ChainSpecification.Inheritance(null, null),
                                      Set.of(),
                                      Set.of());
    }

}
