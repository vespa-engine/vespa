// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.sourceref;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.processing.request.Properties;
import com.yahoo.search.searchchain.model.federation.FederationOptions;

import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Resolves (source, provider) component specifications to a search chain invocation spec.
 * The provider component specification is given by the entry in the queryMap with key
 * 'source.&lt;source-name&gt;.provider'.
 *
 * <p>
 * The diagram shows the relationship between source, provider and the result:
 * (source is used to select row, provider is used to select column.)
 * Provider id = null is used for regular search chains.
 * </p>
 *
 * <pre>
 *                   Provider id
 *                 null
 *                |----+---+---+---|
 *                | o  |   |   |   |
 *                |----+---+---+---|
 * Source id      |    | o | o |   |
 *                |----+---+---+---|
 *                |    |   |   | o |
 *                |----+---+---+---|
 *
 *                    o: SearchChainInvocationSpec
 * </pre>
 *
 * @author Tony Vaagenes
 */
public class SearchChainResolver {

    private final ComponentRegistry<Target> targets;
    private final SortedSet<Target> defaultTargets;

    public static class Builder {

        private final SortedSet<Target> defaultTargets = new TreeSet<>();

        private final ComponentRegistry<Target> targets = new ComponentRegistry<>() {
            @Override
            public void freeze() {
                allComponents().forEach(Target::freeze);
                super.freeze();
            }
        };

        public Builder addSearchChain(ComponentId searchChainId) {
            return addSearchChain(searchChainId, Collections.<String>emptyList());
        }

        public Builder addSearchChain(ComponentId searchChainId, FederationOptions federationOptions) {
            return addSearchChain(searchChainId, federationOptions, Collections.<String>emptyList());
        }

        public Builder addSearchChain(ComponentId searchChainId, List<String> documentTypes) {
            return addSearchChain(searchChainId, new FederationOptions(), documentTypes);
        }

        public Builder addSearchChain(ComponentId searchChainId,
                                      FederationOptions federationOptions,
                                      List<String> documentTypes) {
            registerTarget(new SingleTarget(searchChainId,
                                            new SearchChainInvocationSpec(searchChainId,
                                                                          null,
                                                                          null,
                                                                          federationOptions,
                                                                          documentTypes),
                                            false));
            return this;
        }

        private Builder registerTarget(SingleTarget singleTarget) {
            targets.register(singleTarget.getId(), singleTarget);
            if (singleTarget.useByDefault()) {
                defaultTargets.add(singleTarget);
            }
            return this;
        }

        public Builder addSourceForProvider(ComponentId sourceId, ComponentId providerId, ComponentId searchChainId,
                                            boolean isDefaultProviderForSource, FederationOptions federationOptions,
                                            List<String> documentTypes) {

            SearchChainInvocationSpec searchChainInvocationSpec =
                    new SearchChainInvocationSpec(searchChainId, sourceId, providerId, federationOptions, documentTypes);

            SourcesTarget sourcesTarget = getOrRegisterSourceTarget(sourceId);
            sourcesTarget.addSource(providerId, searchChainInvocationSpec, isDefaultProviderForSource);

            registerTarget(new SingleTarget(searchChainId, searchChainInvocationSpec, true));
            return this;
        }

        private SourcesTarget getOrRegisterSourceTarget(ComponentId sourceId) {
            Target sourcesTarget = targets.getComponent(sourceId);
            if (sourcesTarget == null) {
                targets.register(sourceId, new SourcesTarget(sourceId));
                return getOrRegisterSourceTarget(sourceId);
            } else if (sourcesTarget instanceof SourcesTarget) {
                return (SourcesTarget) sourcesTarget;
            } else {
                throw new IllegalStateException("Expected " + sourceId + " to be a source.");
            }
        }

        public void useTargetByDefault(String targetId) {
            Target target = targets.getComponent(targetId);
            assert target != null : "Target not added yet.";

            defaultTargets.add(target);
        }

        public SearchChainResolver build() {
            targets.freeze();
            return new SearchChainResolver(targets, defaultTargets);
        }
    }

    private SearchChainResolver(ComponentRegistry<Target> targets, SortedSet<Target> defaultTargets) {
        this.targets = targets;
        this.defaultTargets = Collections.unmodifiableSortedSet(defaultTargets);
    }


    public SearchChainInvocationSpec resolve(ComponentSpecification sourceRef, Properties sourceToProviderMap)
            throws UnresolvedSearchChainException {

        Target target = resolveTarget(sourceRef);
        return target.responsibleSearchChain(sourceToProviderMap);
    }

    private Target resolveTarget(ComponentSpecification sourceRef) throws UnresolvedSearchChainException {
        Target target = targets.getComponent(sourceRef);
        if (target == null) {
            throw UnresolvedSourceRefException.createForMissingSourceRef(sourceRef);
        }
        return target;
    }

    public SortedSet<Target> allTopLevelTargets() {
        SortedSet<Target> topLevelTargets = new TreeSet<>();
        for (Target target : targets.allComponents()) {
            if (!target.isDerived) {
                topLevelTargets.add(target);
            }
        }
        return topLevelTargets;
    }

    public SortedSet<Target> defaultTargets() {
        return defaultTargets;
    }

}
