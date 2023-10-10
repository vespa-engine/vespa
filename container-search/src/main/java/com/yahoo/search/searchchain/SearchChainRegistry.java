// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.processing.execution.chain.ChainRegistry;
import com.yahoo.search.Searcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contains a reference to all currently known search chains.
 * Searchers can be fetched from this from multiple threads.
 * <p>
 * A registry can exist in two states:
 * <ul>
 * <li>not frozen - in this state it can be edited freely by calling {@link #register}
 * <li>frozen - in this state any attempt at modification throws an IlegalStateException
 * </ul>
 * Registries start in the first state, moves to the second on calling freeze and stays in that
 * state for the rest of their lifetime.
 *
 * @author bratseth
 */
public class SearchChainRegistry extends ChainRegistry<Searcher> {

    private final SearcherRegistry searcherRegistry;

    @Override
    public void freeze() {
        super.freeze();
        getSearcherRegistry().freeze();
    }

    public SearchChainRegistry() {
        searcherRegistry = new SearcherRegistry();
        searcherRegistry.freeze();
    }

    public SearchChainRegistry(ComponentRegistry<? extends AbstractComponent> allComponentRegistry) {
        this.searcherRegistry = setupSearcherRegistry(allComponentRegistry);
    }

    public void register(Chain<Searcher> component) {
        super.register(component.getId(), component);
    }

    public Chain<Searcher> unregister(Chain<Searcher> component) {
        return super.unregister(component.getId());
    }

    private SearcherRegistry setupSearcherRegistry(ComponentRegistry<? extends AbstractComponent> allComponents) {
        SearcherRegistry registry = new SearcherRegistry();
        for (AbstractComponent component : allComponents.allComponents()) {
            if (component instanceof Searcher) {
                registry.register((Searcher) component);
            }
        }
        //just freeze this right away
        registry.freeze();
        return registry;
    }

    public SearcherRegistry getSearcherRegistry() {
        return searcherRegistry;
    }

    @Override
    public SearchChain getComponent(ComponentId id) {
        Chain<Searcher> chain = super.getComponent(id);
        return asSearchChain(chain);
    }

    @Override
    public SearchChain getComponent(ComponentSpecification specification) {
        return asSearchChain(super.getComponent(specification));
    }

    public final Chain<Searcher> getChain(String componentSpecification) {
        return super.getComponent(new ComponentSpecification(componentSpecification));
    }

    public final Chain<Searcher> getChain(ComponentId id) {
         return super.getComponent(id);
     }


    @Override
    public SearchChain getComponent(String componentSpecification) {
        return getComponent(new ComponentSpecification(componentSpecification));
    }

    private SearchChain asSearchChain(Chain<Searcher> chain) {
        if (chain == null) {
            return null;
        } else if (chain instanceof SearchChain) {
            return (SearchChain) chain;
        } else {
            return new SearchChain(chain);
        }
    }


}
