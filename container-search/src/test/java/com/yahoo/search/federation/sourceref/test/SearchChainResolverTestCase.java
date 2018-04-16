// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.sourceref.test;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.processing.request.properties.PropertyMap;
import com.yahoo.processing.request.Properties;
import com.yahoo.search.federation.sourceref.SearchChainInvocationSpec;
import com.yahoo.search.federation.sourceref.SearchChainResolver;
import com.yahoo.search.federation.sourceref.Target;
import com.yahoo.search.federation.sourceref.UnresolvedSearchChainException;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import org.junit.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.SortedSet;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author tonytv
 */
public class SearchChainResolverTestCase {

    private static final FederationOptions federationOptions =
            new FederationOptions().setTimeoutInMilliseconds(3000).setOptional(true);

    private static final ComponentId searchChainId = ComponentId.fromString("search-chain");
    private static final ComponentId providerId = ComponentId.fromString("provider");
    private static final ComponentId provider2Id = ComponentId.fromString("provider2");

    private static final ComponentId sourceId = ComponentId.fromString("source");
    private static final ComponentId sourceChainInProviderId =
            ComponentId.fromString("source-chain").nestInNamespace(providerId);
    private static final ComponentId sourceChainInProvider2Id =
            ComponentId.fromString("source-chain").nestInNamespace(provider2Id);

    private static final SearchChainResolver searchChainResolver;

    static {
        SearchChainResolver.Builder builder = new SearchChainResolver.Builder();
        builder.addSearchChain(searchChainId, federationOptions.setUseByDefault(true), Collections.<String>emptyList());
        builder.addSearchChain(providerId, federationOptions.setUseByDefault(false), Collections.<String>emptyList());
        builder.addSourceForProvider(sourceId, providerId, sourceChainInProviderId, true,
                federationOptions.setUseByDefault(true), Collections.<String>emptyList());
        builder.addSourceForProvider(sourceId, provider2Id, sourceChainInProvider2Id, false,
                federationOptions.setUseByDefault(false), Collections.<String>emptyList());

        searchChainResolver = builder.build();
    }

    @Test
    public void check_default_search_chains() {
        assertThat(searchChainResolver.defaultTargets().size(), is(2));

        Iterator<Target> iterator = searchChainResolver.defaultTargets().iterator();
        assertThat(iterator.next().searchRefDescription(), is(searchChainId.toString()));
        assertThat(iterator.next().searchRefDescription(), is(sourceChainInProviderId.toString()));
    }

    @Test
    public void require_error_message_for_invalid_source() {
        try {
            resolve("no-such-source");
            fail("Expected exception.");
        } catch (UnresolvedSearchChainException e) {
            assertThat(e.getMessage(), is("Could not resolve source ref 'no-such-source'."));
        }
    }

    @Test
    public void lookup_search_chain() throws Exception {
        SearchChainInvocationSpec res = resolve(searchChainId.getName());
        assertThat(res.searchChainId, is(searchChainId));
    }

    //TODO: TVT: @Test()
    public void lookup_provider() throws Exception {
        SearchChainInvocationSpec res = resolve(providerId.getName());
        assertThat(res.provider, is(providerId));
        assertNull(res.source);
        assertThat(res.searchChainId, is(providerId));
    }

    @Test
    public void lookup_source() throws Exception {
        SearchChainInvocationSpec res = resolve(sourceId.getName());
        assertIsSourceInProvider(res);
    }

    @Test
    public void lookup_source_search_chain_directly() throws Exception {
        SearchChainInvocationSpec res = resolve(sourceChainInProviderId.stringValue());
        assertIsSourceInProvider(res);
    }

    private void assertIsSourceInProvider(SearchChainInvocationSpec res) {
        assertThat(res.provider, is(providerId));
        assertThat(res.source, is(sourceId));
        assertThat(res.searchChainId, is(sourceChainInProviderId));
    }

    @Test
    public void lookup_source_for_provider2() throws Exception {
        SearchChainInvocationSpec res = resolve(sourceId.getName(), provider2Id.getName());
        assertThat(res.provider, is(provider2Id));
        assertThat(res.source, is(sourceId));
        assertThat(res.searchChainId, is(sourceChainInProvider2Id));
    }

    @Test
    public void lists_source_ref_description_for_top_level_targets() {
        SortedSet<Target> topLevelTargets = searchChainResolver.allTopLevelTargets();
        assertThat(topLevelTargets.size(), is(3));

        Iterator<Target> i = topLevelTargets.iterator();
        assertSearchRefDescriptionIs(i.next(), providerId.toString());
        assertSearchRefDescriptionIs(i.next(), searchChainId.toString());
        assertSearchRefDescriptionIs(i.next(), "source[provider = provider, provider2]");
    }

    private void assertSearchRefDescriptionIs(Target target, String expected) {
        assertThat(target.searchRefDescription(), is(expected));
    }

    static Properties emptySourceToProviderMap() {
        return new PropertyMap();
    }

    private SearchChainInvocationSpec resolve(String sourceSpecification) throws UnresolvedSearchChainException {
        return resolve(sourceSpecification, emptySourceToProviderMap());
    }

    private SearchChainInvocationSpec resolve(String sourceSpecification, String providerSpecification)
            throws UnresolvedSearchChainException {
        Properties sourceToProviderMap = emptySourceToProviderMap();
        sourceToProviderMap.set("source." + sourceSpecification + ".provider", providerSpecification);
        return resolve(sourceSpecification, sourceToProviderMap);
    }

    private SearchChainInvocationSpec resolve(String sourceSpecification, Properties sourceToProviderMap)
            throws UnresolvedSearchChainException {
        SearchChainInvocationSpec res = searchChainResolver.resolve(
                ComponentSpecification.fromString(sourceSpecification), sourceToProviderMap);
        assertThat(res.federationOptions, is(federationOptions));
        return res;
    }

}
