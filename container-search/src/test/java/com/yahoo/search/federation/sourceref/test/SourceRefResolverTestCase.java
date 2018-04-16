// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.sourceref.test;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.search.federation.sourceref.SearchChainInvocationSpec;
import com.yahoo.search.federation.sourceref.SearchChainResolver;
import com.yahoo.search.federation.sourceref.SourceRefResolver;
import com.yahoo.search.federation.sourceref.UnresolvedSearchChainException;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import static com.yahoo.search.federation.sourceref.test.SearchChainResolverTestCase.emptySourceToProviderMap;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.matchers.JUnitMatchers.hasItems;


/**
 * Test for SourceRefResolver.
 * @author tonytv
 */
public class SourceRefResolverTestCase {
    private static final String cluster1 = "cluster1";
    private static final String cluster2 = "cluster2";
    private static final String cluster3 = "cluster3";
    private static IndexFacts indexFacts;

    private static final SourceRefResolver sourceRefResolver = createSourceRefResolver();

    static {
        setupIndexFacts();
    }

    private static SourceRefResolver createSourceRefResolver() {
        SearchChainResolver.Builder builder = new SearchChainResolver.Builder();
        builder.addSearchChain(ComponentId.fromString(cluster1), new FederationOptions().setUseByDefault(true),
                Collections.<String>emptyList());
        builder.addSearchChain(ComponentId.fromString(cluster2), new FederationOptions().setUseByDefault(true),
                Collections.<String>emptyList());

        return new SourceRefResolver(builder.build());
    }

    private static void setupIndexFacts() {
        TreeMap<String, List<String>> masterClusters = new TreeMap<>();
        masterClusters.put(cluster1, Arrays.asList("document1", "document2"));
        masterClusters.put(cluster2, Arrays.asList("document1"));
        masterClusters.put(cluster3, Arrays.asList("document3"));
        indexFacts = new IndexFacts(new IndexModel(masterClusters, null, null));
    }

    @Test
    public void check_test_assumptions() {
        assertThat(indexFacts.clustersHavingSearchDefinition("document1"), hasItems("cluster1", "cluster2"));
    }

    @Test
    public void lookup_search_chain() throws Exception {
        Set<SearchChainInvocationSpec> searchChains = resolve(cluster1);
        assertThat(searchChains.size(), is(1));
        assertThat(searchChainIds(searchChains), hasItems(cluster1));
    }

    @Test
    public void lookup_search_chains_for_document1() throws Exception {
        Set<SearchChainInvocationSpec> searchChains = resolve("document1");
        assertThat(searchChains.size(), is(2));
        assertThat(searchChainIds(searchChains), hasItems(cluster1, cluster2));
    }

    @Test
    public void error_when_document_gives_cluster_without_matching_search_chain() {
        try {
            resolve("document3");
            fail("Expected exception");
        } catch (UnresolvedSearchChainException e) {
            assertThat(e.getMessage(), is("Failed to resolve cluster search chain 'cluster3' " +
            "when using source ref 'document3' as a document name."));
        }
    }

    @Test
    public void error_when_no_document_or_search_chain() {
        try {
            resolve("document4");
            fail("Expected exception");
        } catch (UnresolvedSearchChainException e) {
            assertThat(e.getMessage(), is("Could not resolve source ref 'document4'."));
        }
    }

    private List<String> searchChainIds(Set<SearchChainInvocationSpec> searchChains) {
        List<String> names = new ArrayList<>();
        for (SearchChainInvocationSpec searchChain : searchChains) {
            names.add(searchChain.searchChainId.stringValue());
        }
        return names;
    }

    private Set<SearchChainInvocationSpec> resolve(String documentName) throws UnresolvedSearchChainException {
        return sourceRefResolver.resolve(ComponentSpecification.fromString(documentName), emptySourceToProviderMap(), indexFacts);
    }
}
