// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Phase;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.hamcrest.Matchers.containsString;

/**
 * @author Tony Vaagenes
 */
public class SourceGroupTest {
    private MockRoot root;
    private SearchChains searchChains;

    @Before
    public void setUp() throws Exception {
        root = new MockRoot();
        searchChains = new SearchChains(root, "searchchains");
    }

    @Test
    public void report_error_when_no_leader() {
        try {
            Provider provider = createProvider("p1");
            Source source = createSource("s1", Source.GroupOption.participant);
            provider.addSource(source);

            searchChains.add(provider);
            root.freezeModelTopology();

            searchChains.validate();
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("Missing leader for the source s1."));
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
                Collections.<Phase>emptyList(),
                Collections.<ComponentSpecification>emptySet());
    }

    private Source createSource(String sourceId, Source.GroupOption groupOption) {
        return new Source(
                createSearchChainSpecification(sourceId),
                new FederationOptions(),
                groupOption);
    }

    @Test
    public void require_that_source_and_provider_id_is_not_allowed_to_be_equal() {
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
            assertThat(e.getMessage(), containsString("Same id used for a source"));
            assertThat(e.getMessage(), containsString("'sameId'"));
        }
    }
}
