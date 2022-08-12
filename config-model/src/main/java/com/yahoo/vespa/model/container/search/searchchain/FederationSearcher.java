// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.collections.CollectionUtil;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import com.yahoo.search.searchchain.model.federation.FederationSearcherModel;
import com.yahoo.search.federation.FederationConfig;
import com.yahoo.search.searchchain.model.federation.FederationSearcherModel.TargetSpec;
import com.yahoo.vespa.model.container.component.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Config producer for the FederationSearcher.
 *
 * @author Tony Vaagenes
 */
public class FederationSearcher extends Searcher<FederationSearcherModel> implements FederationConfig.Producer {

    private final Optional<Component> targetSelector;

    /**
     * Generates config for a single search chain contained in a target.
     */
    private static final class SearchChainConfig {

        private final SearchChain searchChain;
        final ComponentId providerId;
        final FederationOptions targetOptions;
        final List<String> documentTypes;

        SearchChainConfig(SearchChain searchChain, ComponentId providerId,
                          FederationOptions targetOptions, List<String> documentTypes) {
            this.searchChain = searchChain;
            this.providerId = providerId;
            this.targetOptions = targetOptions;
            this.documentTypes = documentTypes;
        }

        FederationConfig.Target.SearchChain.Builder getSearchChainConfig() {
            FederationConfig.Target.SearchChain.Builder sB = new FederationConfig.Target.SearchChain.Builder();
            FederationOptions resolvedOptions = targetOptions.inherit(searchChain.federationOptions());
            sB.
                searchChainId(searchChain.getGlobalComponentId().stringValue()).
                timeoutMillis(resolvedOptions.getTimeoutInMilliseconds()).
                requestTimeoutMillis(resolvedOptions.getRequestTimeoutInMilliseconds()).
                optional(resolvedOptions.getOptional()).
                useByDefault(resolvedOptions.getUseByDefault()).
                documentTypes(documentTypes);
            if (providerId != null)
                sB.providerId(providerId.stringValue());
            return sB;
        }
    }

    /**
     * One or more search chains that are handled as a single group,
     * which can be federated to as a single entity.
     */
    private static abstract class Target {

        final ComponentId id;
        final FederationOptions targetOptions;

        Target(ComponentId id, FederationOptions targetOptions) {
            this.id = id;
            this.targetOptions = targetOptions;
        }

        FederationConfig.Target.Builder getTargetConfig() {
            FederationConfig.Target.Builder tb = new FederationConfig.Target.Builder();
            tb.
                id(id.stringValue()).
                useByDefault(targetOptions.getUseByDefault());
            getSearchChainsConfig(tb);
            return tb;
        }

        protected abstract void getSearchChainsConfig(FederationConfig.Target.Builder tb);

    }

    private static class SearchChainTarget extends Target {

        private final SearchChainConfig searchChainConfig;

        SearchChainTarget(SearchChain searchChain, FederationOptions targetOptions) {
            super(searchChain.getComponentId(), targetOptions);
            searchChainConfig = new SearchChainConfig(searchChain, null, targetOptions, searchChain.getDocumentTypes());
        }

        @Override
        protected void getSearchChainsConfig(FederationConfig.Target.Builder tB) {
            tB.searchChain(searchChainConfig.getSearchChainConfig());
        }

    }

    private static class SourceGroupTarget extends Target {

        private final SearchChainConfig leaderConfig;
        private final List<SearchChainConfig> participantsConfig = new ArrayList<>();

        public SourceGroupTarget(SourceGroup group, FederationOptions targetOptions) {
            super(group.getComponentId(), applyDefaultSourceGroupOptions(targetOptions));

            leaderConfig = createConfig(group.leader(), targetOptions);
            for (Source participant : group.participants())
                participantsConfig.add(createConfig(participant, targetOptions));
        }

        private static FederationOptions applyDefaultSourceGroupOptions(FederationOptions targetOptions) {
            FederationOptions defaultSourceGroupOption = new FederationOptions().setUseByDefault(true);
            return targetOptions.inherit(defaultSourceGroupOption);
        }

        private SearchChainConfig createConfig(Source source, FederationOptions targetOptions) {
            return new SearchChainConfig(source,
                                         source.getParentProvider().getComponentId(),
                                         targetOptions,
                                         source.getDocumentTypes());
        }

        @Override
        protected void getSearchChainsConfig(FederationConfig.Target.Builder tB) {
            tB.searchChain(leaderConfig.getSearchChainConfig());
            for (SearchChainConfig participant : participantsConfig)
                tB.searchChain(participant.getSearchChainConfig());
        }
    }

    private static class TargetResolver {

        final ComponentRegistry<SearchChain> searchChainRegistry;
        final SourceGroupRegistry sourceGroupRegistry;

        /** Returns true if searchChain.id newer than sourceGroup.id */
        private boolean newerVersion(SearchChain searchChain, SourceGroup sourceGroup) {
            if (searchChain == null || sourceGroup == null) return false;
            return newerVersion(searchChain.getComponentId(), sourceGroup.getComponentId());
        }

        /** Returns true if a newer than b */
        private boolean newerVersion(ComponentId a, ComponentId b) {
            return a.compareTo(b) > 0;
        }

        TargetResolver(ComponentRegistry<SearchChain> searchChainRegistry, SourceGroupRegistry sourceGroupRegistry) {
            this.searchChainRegistry = searchChainRegistry;
            this.sourceGroupRegistry = sourceGroupRegistry;
        }

        Target resolve(FederationSearcherModel.TargetSpec specification) {
            SearchChain searchChain = searchChainRegistry.getComponent(specification.sourceSpec);
            SourceGroup sourceGroup = sourceGroupRegistry.getComponent(specification.sourceSpec);

            if (searchChain == null && sourceGroup == null) {
                return null;
            } else if (sourceGroup == null || newerVersion(searchChain, sourceGroup)) {
                return new SearchChainTarget(searchChain, specification.federationOptions);
            } else {
                return new SourceGroupTarget(sourceGroup, specification.federationOptions);
            }
        }
    }

    private final Map<ComponentId, Target> resolvedTargets = new LinkedHashMap<>();

    public FederationSearcher(FederationSearcherModel searcherModel, Optional<Component> targetSelector) {
        super(searcherModel);
        this.targetSelector = targetSelector;

        targetSelector.ifPresent(selector -> addChild(selector));
    }

    @Override
    public void getConfig(FederationConfig.Builder builder) {
        for (Target target : resolvedTargets.values())
            builder.target(target.getTargetConfig());

        targetSelector.ifPresent(selector -> builder.targetSelector(selector.getGlobalComponentId().stringValue()));
    }

    @Override
    public void initialize() {
        initialize(getSearchChains().allChains(), getSearchChains().allSourceGroups());
    }

    void initialize(ComponentRegistry<SearchChain> searchChainRegistry, SourceGroupRegistry sourceGroupRegistry) {
        TargetResolver targetResolver = new TargetResolver(searchChainRegistry, sourceGroupRegistry);

        addSourceTargets(targetResolver, model.targets);

        if (model.inheritDefaultSources)
            addDefaultTargets(targetResolver, searchChainRegistry);
    }

    private void addSourceTargets(TargetResolver targetResolver, List<TargetSpec> targets) {
        for (TargetSpec targetSpec : targets) {

            Target target = targetResolver.resolve(targetSpec);
            if (target == null) {
                throw new IllegalArgumentException("Can't find source " + targetSpec.sourceSpec +
                                                   " used as a source for federation '" + getComponentId() + "'");
            }

            Target duplicate = resolvedTargets.put(target.id, target);
            if (duplicate != null && !duplicate.targetOptions.equals(target.targetOptions)) {
                throw new IllegalArgumentException("Search chain " + target.id + " added twice with different federation options" +
                                                   " to the federation searcher " + getComponentId());
            }
        }
    }


    private void addDefaultTargets(TargetResolver targetResolver, ComponentRegistry<SearchChain> searchChainRegistry) {
        for (GenericTarget genericTarget : defaultTargets(searchChainRegistry.allComponents())) {
            ComponentSpecification specification = genericTarget.getComponentId().toSpecification();

            // Can't use genericTarget directly, as it might be part of a source group.
            Target federationTarget = targetResolver.resolve(new TargetSpec(specification, new FederationOptions()));
            // Do not replace manually added sources, as they might have manually configured federation options
            if (!resolvedTargets.containsKey(federationTarget.id))
                resolvedTargets.put(federationTarget.id, federationTarget);
        }
    }

    private static List<GenericTarget> defaultTargets(Collection<SearchChain> allSearchChains) {
        Collection<Provider> providers = CollectionUtil.filter(allSearchChains, Provider.class);

        List<GenericTarget> defaultTargets = new ArrayList<>();
        for (Provider provider : providers)
            defaultTargets.addAll(provider.defaultFederationTargets());
        return defaultTargets;
    }

}
