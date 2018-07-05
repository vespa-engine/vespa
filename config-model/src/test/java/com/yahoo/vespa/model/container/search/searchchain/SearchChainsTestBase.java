// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.binaryprefix.BinaryPrefix;
import com.yahoo.binaryprefix.BinaryScaledAmount;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.vespa.model.builder.xml.dom.chains.search.DomSearchChainsBuilder;
import org.junit.Before;
import org.w3c.dom.Element;

/** Creates SearchChains model from xml input.
 * @author Tony Vaagenes
 */
public abstract class SearchChainsTestBase extends DomBuilderTest {

    @Before
    public void setupSearchChains() {
        SearchChains searchChains = new DomSearchChainsBuilder().build(root, servicesXml());
        searchChains.initialize(MockSearchClusters.twoMockClusterSpecsByName(root),
                                new BinaryScaledAmount(100, BinaryPrefix.mega));
        root.freezeModelTopology();
    }

    abstract Element servicesXml();
}
