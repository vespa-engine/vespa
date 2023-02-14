// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component.chain;

import com.yahoo.component.chain.model.ChainsModel;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.container.component.ComponentGroup;
import com.yahoo.vespa.model.container.component.ConfigProducerGroup;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Root config producer the whole chains model(contains chains and components).
 *
 * @author Tony Vaagenes
 * @author gjoranv
 */
public class Chains<CHAIN extends Chain<?>>
        extends TreeConfigProducer<AnyConfigProducer>
        implements ChainsConfig.Producer {

    private final ComponentGroup<ChainedComponent<?>> componentGroup;
    private final ConfigProducerGroup<CHAIN> chainGroup;

    public Chains(TreeConfigProducer<? super Chains> parent, String subId) {
        super(parent, subId);
        componentGroup = new ComponentGroup<>(this, "component");
        chainGroup = new ConfigProducerGroup<>(this, "chain");
    }

    public void initializeComponents() {
        for (ChainedComponent component : allComponents()) {
            component.initialize();
        }
    }

    public void validate() throws Exception {
        ChainsModel chainsModel = new ChainsModel();

        for (CHAIN chain : allChains().allComponents()) {
            chainsModel.register(chain.getChainSpecification());
        }
        for (ChainedComponent<?> component : allComponents()) {
            chainsModel.register(component.getGlobalComponentId(), component.model);
        }
        chainsModel.validate();

        super.validate();
    }

    public Set<ChainedComponent<?>> allComponents() {
        Set<ChainedComponent<?>> result = new LinkedHashSet<>();
        result.addAll(componentGroup.getComponents());

        for (CHAIN chain : allChains().allComponents()) {
            result.addAll(chain.getInnerComponents());
        }
        return result;
    }

    public ComponentRegistry<ChainedComponent<?>> componentsRegistry() {
        ComponentRegistry<ChainedComponent<?>> result = new ComponentRegistry<>();

        for (ChainedComponent<?> component: componentGroup.getComponents())
            result.register(component.getGlobalComponentId(), component);

        for (CHAIN chain : allChains().allComponents()) {
            for (ChainedComponent<?> component: chain.getInnerComponents()) {
                result.register(component.getGlobalComponentId(), component);
            }
        }
        return result;
    }

    public ComponentRegistry<CHAIN> allChains() {
        ComponentRegistry<CHAIN> allChains = new ComponentRegistry<>();
        for (CHAIN chain : chainGroup.getComponents()) {
            allChains.register(chain.getId(), chain);
        }
        allChains.freeze();
        return allChains;
    }

    public void add(CHAIN chain) {
        chainGroup.addComponent(chain.getId(), chain);
    }

    public void add(ChainedComponent outerComponent) {
        componentGroup.addComponent(outerComponent);
    }

    @Override
    public void getConfig(ChainsConfig.Builder builder) {
        ChainsConfigGenerator.generate(builder, allChains().allComponents());
        ChainedComponentConfigGenerator.generate(builder, allComponents());
    }

    public ConfigProducerGroup<ChainedComponent<?>> getComponentGroup() {
        return componentGroup;
    }

    protected ConfigProducerGroup<CHAIN> getChainGroup() {
        return chainGroup;
    }

}
