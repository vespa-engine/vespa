// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Tony Vaagenes
 */
public class SourceGroupTest {
    private MockRoot root;
    private SearchChains searchChains;

    @BeforeEach
    public void setUp() {
        root = new MockRoot();
        searchChains = new SearchChains(root, "searchchains");
    }

    @Test
    void report_error_when_no_leader() {
        try {
            Provider provider = createProvider("p1");
            Source source = createSource("s1", Source.GroupOption.participant);
            provider.addSource(source);

            searchChains.add(provider);
            root.freezeModelTopology();

            searchChains.validate();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Missing leader for the source s1."));
            return;
        }
        fail("Expected exception");
    }

    private Provider createProvider(String p1) {
        return new Provider(createSearchChainSpecification(p1), new FederationOptions());
    }

    private ChainSpecification createSearchChainSpecification(String id) {
        return new ChainSpecification(ComponentId.fromString(id),
                new ChainSpecification.Inheritance(null, null),
                List.of(),
                Set.of());
    }

    private Source createSource(String sourceId, Source.GroupOption groupOption) {
        return new Source(
                createSearchChainSpecification(sourceId),
                new FederationOptions(),
                groupOption);
    }

    @Test
    void require_that_source_and_provider_id_is_not_allowed_to_be_equal() {
        Provider provider = createProvider("sameId");
        Provider provider2 = createProvider("ignoredId");

        Source source = createSource("sameId", Source.GroupOption.leader);

        provider2.addSource(source);

        searchChains.add(provider);
        searchChains.add(provider2);
        root.freezeModelTopology();

        try {
            searchChains.validate();
            fail("Expected exception");
        } catch (Exception e) {
            assertEquals("Id 'sameId' is used both for a source and another search chain/provider",
                    e.getMessage());
        }
    }
}
