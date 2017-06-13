// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.vespa.model.builder.xml.dom.chains.search.DomSearchChainsBuilder;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilderTest;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.hamcrest.Matchers.containsString;

/**
 * @author gjoranv
 * @since 5.1.11
 */
public class SearchChainsTest2 {

    private MockRoot root;

    @Before
    public void prepareTest() throws Exception {
        root = new MockRoot("root");
    }

    @Test
    public void fail_upon_unresolved_inheritance() {
        final Element searchElem = DomBuilderTest.parse(
                "<search>",
                "  <chain id='default' inherits='nonexistent' />",
                "</search>");
        try {
            SearchChains chains = new DomSearchChainsBuilder().build(new MockRoot(), searchElem);
            chains.validate();
            fail("Expected exception when inheriting a nonexistent search chain.");
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("Missing chain 'nonexistent'"));
        }
    }

    @Test
    public void fail_upon_two_user_declared_chains_with_same_name() {
        final Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='cluster1' version='1.0'>",
                ContainerModelBuilderTest.nodesXml,
                "  <search>",
                "    <chain id='same' />",
                "    <chain id='same' />",
                "  </search>",
                "</jdisc>");
        try {
            ContainerModelBuilderTest.createModel(root, clusterElem);
            fail("Expected exception when declaring chains with duplicate id.");
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("Two entities have the same component id 'same'"));
        }
    }

    @Test
    public void fail_upon_user_declared_chain_with_same_id_as_builtin_chain() throws Exception {
        final Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='cluster1' version='1.0'>",
                ContainerModelBuilderTest.nodesXml,
                "  <search>",
                "    <chain id='vespa' />",
                "  </search>",
                "</jdisc>");
        try {
            ContainerModelBuilderTest.createModel(root, clusterElem);
            fail("Expected exception when taking the id from a builtin chain.");
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("Two entities have the same component id 'vespa'"));
        }
    }
}
