// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.sourceref;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.yahoo.search.federation.sourceref.SearchChainResolverTestCase.emptySourceToProviderMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for SourceRefResolver.
 *
 * @author Tony Vaagenes
 */
public class SourceRefResolverTestCase {

    private static final String cluster1 = "cluster1";
    private static final String cluster2 = "cluster2";
    private static final String cluster3 = "cluster3";
    private static final String schema1 = "document1";
    private static final String schema2 = "document2";
    private static final String schema3 = "document3";

    private static final SourceRefResolver sourceRefResolver = createSourceRefResolver();

    private static SourceRefResolver createSourceRefResolver() {
        SearchChainResolver.Builder builder = new SearchChainResolver.Builder();
        builder.addSearchChain(ComponentId.fromString(cluster1), new FederationOptions().setUseByDefault(true), List.of());
        builder.addSearchChain(ComponentId.fromString(cluster2), new FederationOptions().setUseByDefault(true), List.of());

        return new SourceRefResolver(builder.build(), Map.of(schema1, List.of(cluster1, cluster2),
                                                             schema2, List.of(cluster1),
                                                             schema3, List.of(cluster3)));
    }

    @Test
    void lookup_search_chain() throws Exception {
        Set<SearchChainInvocationSpec> searchChains = resolve(cluster1);
        assertEquals(1, searchChains.size());
        assertTrue(searchChainIds(searchChains).contains(cluster1));
    }

    @Test
    void lookup_search_chains_for_document1() throws Exception {
        Set<SearchChainInvocationSpec> searchChains = resolve("document1");
        assertEquals(2, searchChains.size());
        assertTrue(searchChainIds(searchChains).containsAll(List.of(cluster1, cluster2)));
    }

    @Test
    void error_when_document_gives_cluster_without_matching_search_chain() {
        try {
            resolve("document3");
            fail("Expected exception");
        } catch (UnresolvedSearchChainException e) {
            assertEquals("Failed to resolve cluster search chain 'cluster3' " +
                    "when using source ref 'document3' as a document name.",
                    e.getMessage());
        }
    }

    @Test
    void error_when_no_document_or_search_chain() {
        try {
            resolve("document4");
            fail("Expected exception");
        } catch (UnresolvedSearchChainException e) {
            assertEquals("Could not resolve source ref 'document4'.", e.getMessage());
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
        return sourceRefResolver.resolve(ComponentSpecification.fromString(documentName), emptySourceToProviderMap());
    }

}
