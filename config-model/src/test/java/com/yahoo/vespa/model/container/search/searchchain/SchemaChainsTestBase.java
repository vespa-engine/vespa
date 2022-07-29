// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.vespa.model.builder.xml.dom.chains.search.DomSearchChainsBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.w3c.dom.Element;

/** Creates SearchChains model from xml input.
 * @author Tony Vaagenes
 */
public abstract class SchemaChainsTestBase extends DomBuilderTest {

    @BeforeEach
    public void setupSearchChains() {
        SearchChains searchChains = new DomSearchChainsBuilder().build(root.getDeployState(), root, servicesXml());
        searchChains.initialize(MockSearchClusters.twoMockClusterSpecsByName(root));
        root.freezeModelTopology();
    }

    abstract Element servicesXml();
}
