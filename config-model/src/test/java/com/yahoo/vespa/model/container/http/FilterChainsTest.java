// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.vespa.model.container.component.chain.Chain;
import com.yahoo.vespa.model.container.http.xml.HttpBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.Set;

import static com.yahoo.collections.CollectionUtil.first;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author gjoranv
 * @author Tony Vaagenes
 * @since 5.1.26
 */
public class FilterChainsTest extends DomBuilderTest {
    private Http http;

    @BeforeEach
    public void setupFilterChains() {
        http = new HttpBuilder(Set.of()).build(root.getDeployState(), root, servicesXml());
        root.freezeModelTopology();
    }

    private Element servicesXml() {
        return parse(
                "<http>",
                "  <filtering>",
                "    <filter id='outer' />",
                "    <request-chain id='myChain'>",
                "      <filter id='inner' />",
                "    </request-chain>",
                "  </filtering>",
                "</http>");
    }

    @Test
    void chains_are_built() {
        assertNotNull(getChain("myChain"));
    }

    @Test
    void filters_outside_chains_are_built() {
        Filter outerFilter = (Filter) http.getFilterChains().componentsRegistry().getComponent("outer");
        assertNotNull(outerFilter);
    }

    @Test
    void filters_in_chains_are_built() {
        Filter filter = first(getChain("myChain").getInnerComponents());
        assertNotNull(filter);
        assertEquals("inner", filter.getComponentId().getName());
    }

    @Test
    void request_chain_cannot_inherit_response_chain() {
        var e = assertThrows(IllegalArgumentException.class,
                             () -> buildHttp(parse(
                                     "<http>",
                                     "  <filtering>",
                                     "    <response-chain id='responseChain'>",
                                     "      <filter id='responseFilter' />",
                                     "    </response-chain>",
                                     "    <request-chain id='requestChain' inherits='responseChain'>",
                                     "      <filter id='requestFilter' />",
                                     "    </request-chain>",
                                     "  </filtering>",
                                     "</http>")));
        assertEquals("request-chain 'requestChain' cannot inherit response-chain 'responseChain'", e.getMessage());
    }

    @Test
    void response_chain_cannot_inherit_request_chain() {
        var e = assertThrows(IllegalArgumentException.class,
                             () -> buildHttp(parse(
                                     "<http>",
                                     "  <filtering>",
                                     "    <request-chain id='requestChain'>",
                                     "      <filter id='requestFilter' />",
                                     "    </request-chain>",
                                     "    <response-chain id='responseChain' inherits='requestChain'>",
                                     "      <filter id='responseFilter' />",
                                     "    </response-chain>",
                                     "  </filtering>",
                                     "</http>")));
        assertEquals("response-chain 'responseChain' cannot inherit request-chain 'requestChain'", e.getMessage());
    }

    @Test
    void chain_can_inherit_chain_of_same_type() {
        buildHttp(parse(
                "<http>",
                "  <filtering>",
                "    <request-chain id='parent'>",
                "      <filter id='parentFilter' />",
                "    </request-chain>",
                "    <request-chain id='child' inherits='parent'>",
                "      <filter id='childFilter' />",
                "    </request-chain>",
                "  </filtering>",
                "</http>"));
    }

    private void buildHttp(Element spec) {
        MockRoot mockRoot = new MockRoot();
        new HttpBuilder(Set.of()).build(mockRoot.getDeployState(), mockRoot, spec);
    }

    private Chain<Filter> getChain(String chainName) {
        return http.getFilterChains().allChains().getComponent(ComponentSpecification.fromString(chainName));
    }
}
