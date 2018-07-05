// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.vespa.model.container.component.chain.Chain;
import com.yahoo.vespa.model.container.http.xml.HttpBuilder;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;

import static com.yahoo.collections.CollectionUtil.first;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author gjoranv
 * @author Tony Vaagenes
 * @since 5.1.26
 */
public class FilterChainsTest extends DomBuilderTest {
    private Http http;

    @Before
    public void setupFilterChains() {
        http = new HttpBuilder().build(root, servicesXml());
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
    public void chains_are_built() {
        assertNotNull(getChain("myChain"));
    }

    @Test
    public void filters_outside_chains_are_built() {
        Filter outerFilter = (Filter)http.getFilterChains().componentsRegistry().getComponent("outer");
        assertNotNull(outerFilter);
    }

    @Test
    public void filters_in_chains_are_built() {
        Filter filter = first(getChain("myChain").getInnerComponents());
        assertNotNull(filter);
        assertThat(filter.getComponentId().getName(), is("inner"));
    }

    private Chain<Filter> getChain(String chainName) {
        return http.getFilterChains().allChains().getComponent(ComponentSpecification.fromString(chainName));
    }
}
