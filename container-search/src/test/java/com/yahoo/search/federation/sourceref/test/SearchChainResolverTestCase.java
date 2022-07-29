// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.SortedSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Tony Vaagenes
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
    void check_default_search_chains() {
        assertEquals(2, searchChainResolver.defaultTargets().size());

        Iterator<Target> iterator = searchChainResolver.defaultTargets().iterator();
        assertEquals(searchChainId.toString(), iterator.next().searchRefDescription());
        assertEquals(sourceChainInProviderId.toString(), iterator.next().searchRefDescription());
    }

    @Test
    void require_error_message_for_invalid_source() {
        try {
            resolve("no-such-source");
            fail("Expected exception.");
        } catch (UnresolvedSearchChainException e) {
            assertEquals("Could not resolve source ref 'no-such-source'.", e.getMessage());
        }
    }

    @Test
    void lookup_search_chain() throws Exception {
        SearchChainInvocationSpec res = resolve(searchChainId.getName());
        assertEquals(searchChainId, res.searchChainId);
    }

    //TODO: TVT: @Test()
    public void lookup_provider() throws Exception {
        SearchChainInvocationSpec res = resolve(providerId.getName());
        assertEquals(providerId, res.provider);
        assertNull(res.source);
        assertEquals(providerId, res.searchChainId);
    }

    @Test
    void lookup_source() throws Exception {
        SearchChainInvocationSpec res = resolve(sourceId.getName());
        assertIsSourceInProvider(res);
    }

    @Test
    void lookup_source_search_chain_directly() throws Exception {
        SearchChainInvocationSpec res = resolve(sourceChainInProviderId.stringValue());
        assertIsSourceInProvider(res);
    }

    private void assertIsSourceInProvider(SearchChainInvocationSpec res) {
        assertEquals(providerId, res.provider);
        assertEquals(sourceId, res.source);
        assertEquals(sourceChainInProviderId, res.searchChainId);
    }

    @Test
    void lookup_source_for_provider2() throws Exception {
        SearchChainInvocationSpec res = resolve(sourceId.getName(), provider2Id.getName());
        assertEquals(provider2Id, res.provider);
        assertEquals(sourceId, res.source);
        assertEquals(sourceChainInProvider2Id, res.searchChainId);
    }

    @Test
    void lists_source_ref_description_for_top_level_targets() {
        SortedSet<Target> topLevelTargets = searchChainResolver.allTopLevelTargets();
        assertEquals(3, topLevelTargets.size());

        Iterator<Target> i = topLevelTargets.iterator();
        assertSearchRefDescriptionIs(i.next(), providerId.toString());
        assertSearchRefDescriptionIs(i.next(), searchChainId.toString());
        assertSearchRefDescriptionIs(i.next(), "source[provider = provider, provider2]");
    }

    private void assertSearchRefDescriptionIs(Target target, String expected) {
        assertEquals(expected, target.searchRefDescription());
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
        assertEquals(federationOptions, res.federationOptions);
        return res;
    }

}
