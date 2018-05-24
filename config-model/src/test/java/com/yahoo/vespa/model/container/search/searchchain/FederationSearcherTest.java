// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.search.federation.FederationConfig;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import com.yahoo.search.searchchain.model.federation.FederationSearcherModel;
import com.yahoo.search.searchchain.model.federation.FederationSearcherModel.TargetSpec;
import com.yahoo.vespa.model.ConfigProducer;
import com.yahoo.vespa.model.container.search.searchchain.Source.GroupOption;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class FederationSearcherTest {

    private static class FederationFixture {
        FederationSearcher federationSearchWithDefaultSources = newFederationSearcher(true, emptyList());
        private ComponentRegistry<SearchChain> searchChainRegistry = new ComponentRegistry<>();
        private SourceGroupRegistry sourceGroupRegistry = new SourceGroupRegistry();

        void initializeFederationSearcher(FederationSearcher searcher) {
            searcher.initialize(searchChainRegistry, sourceGroupRegistry);
        }

        void registerProviderWithSources(Provider provider) {
            List<GenericTarget> sources = new ArrayList<>();
            sources.add(provider);
            sources.addAll(provider.getSources());
            for (GenericTarget gt : sources) {
                searchChainRegistry.register(gt.getId(), gt);
            }
            sourceGroupRegistry.addSources(provider);
        }
    }

    private static class ProvidersWithSourceFixture extends FederationFixture {
        Provider provider1 = createProvider(ComponentId.fromString("provider1"));
        Provider provider2 = createProvider(ComponentId.fromString("provider2"));

        private ProvidersWithSourceFixture() {
            super();
            provider1.addSource(createSource(ComponentId.fromString("source"), GroupOption.leader));
            provider2.addSource(createSource(ComponentId.fromString("source"), GroupOption.participant));

            registerProviderWithSources(provider1);
            registerProviderWithSources(provider2);
            initializeFederationSearcher(federationSearchWithDefaultSources);
        }
    }

    @Test
    public void default_providers_are_inherited_when_inheritDefaultSources_is_true() throws Exception {
        FederationFixture f = new FederationFixture();

        final String providerId = "providerId";

        f.registerProviderWithSources(createProvider(ComponentId.fromString(providerId)));
        f.initializeFederationSearcher(f.federationSearchWithDefaultSources);

        FederationConfig federationConfig = getConfig(f.federationSearchWithDefaultSources);
        FederationConfig.Target target = federationConfig.target(0);

        assertSame(providerId, target.id()); // by identity
        assertTrue("Not used by default", target.searchChain(0).useByDefault());
    }

    @Test
    public void source_groups_are_inherited_when_inheritDefaultSources_is_true() throws Exception {
        FederationFixture f = new ProvidersWithSourceFixture();

        FederationConfig federationConfig = getConfig(f.federationSearchWithDefaultSources);
        Assert.assertEquals(1, federationConfig.target().size());

        FederationConfig.Target target = federationConfig.target(0);
        assertEquals(target.id(), "source");
        assertTrue("Not used by default", target.useByDefault());
        assertEquals(2, target.searchChain().size());
        assertThat(target.searchChain().stream().map(FederationConfig.Target.SearchChain::providerId).collect(toList()),
                contains("provider1", "provider2"));
    }

    @Test
    public void source_groups_are_not_inherited_when_inheritDefaultSources_is_false() throws Exception {
        FederationFixture f = new ProvidersWithSourceFixture();

        FederationSearcher federationSearcherWithoutDefaultSources = newFederationSearcher(false, emptyList());
        f.initializeFederationSearcher(federationSearcherWithoutDefaultSources);

        FederationConfig federationConfig = getConfig(federationSearcherWithoutDefaultSources);
        assertEquals(0, federationConfig.target().size());
    }

    @Test
    public void leaders_must_be_the_first_search_chain_in_a_target() throws Exception {
        FederationFixture f = new ProvidersWithSourceFixture();

        FederationConfig federationConfig = getConfig(f.federationSearchWithDefaultSources);
        List<FederationConfig.Target.SearchChain> searchChain = federationConfig.target(0).searchChain();

        assertEquals("provider1", searchChain.get(0).providerId());
        assertEquals("provider2", searchChain.get(1).providerId());
    }

    @Test
    public void manually_specified_targets_overrides_inherited_targets() throws Exception {
        FederationFixture f = new FederationFixture();

        f.registerProviderWithSources(createProvider(ComponentId.fromString("provider1")));
        FederationSearcher federation = newFederationSearcher(true,
                singletonList(new TargetSpec(ComponentSpecification.fromString("provider1"),
                        new FederationOptions().setTimeoutInMilliseconds(12345))));
        f.initializeFederationSearcher(federation);

        FederationConfig federationConfig = getConfig(federation);
        assertEquals(1, federationConfig.target().size());

        FederationConfig.Target target = federationConfig.target(0);
        assertEquals(1, target.searchChain().size());

        FederationConfig.Target.SearchChain searchChain = target.searchChain(0);
        assertEquals(12345, searchChain.timeoutMillis());
    }

    private static FederationSearcher newFederationSearcher(boolean inheritDefaultSources, List<TargetSpec> targets) {
        return new FederationSearcher(new FederationSearcherModel(ComponentSpecification.fromString("federation"),
                Dependencies.emptyDependencies(), targets, inheritDefaultSources), Optional.empty());
    }

    private static ChainSpecification searchChainSpecification(ComponentId id) {
        return new ChainSpecification(id, new ChainSpecification.Inheritance(null, null), emptyList(), emptySet());
    }

    private static Provider createProvider(ComponentId id) {
        return new Provider(searchChainSpecification(id), new FederationOptions());
    }

    private static Source createSource(ComponentId id, GroupOption groupOption) {
        return new Source(searchChainSpecification(id), new FederationOptions(), groupOption);
    }

    private static FederationConfig getConfig(ConfigProducer configProducer) throws Exception {
        Optional<Class<?>> builderClassOpt = Arrays.stream(FederationConfig.class.getDeclaredClasses())
                .filter(c -> c.getSimpleName().equals("Builder")).findFirst();
        if (builderClassOpt.isPresent() == false) {
            throw new RuntimeException("No Builder class in ConfigInstance.");
        }
        Class<?> builderClass = builderClassOpt.get();

        Object builder = builderClass.getDeclaredConstructor().newInstance();
        Method getConfigMethod = configProducer.getClass().getMethod("getConfig", builderClass);

        getConfigMethod.invoke(configProducer, builder);

        return FederationConfig.class.getConstructor(builderClass).newInstance(builder);
    }
}
