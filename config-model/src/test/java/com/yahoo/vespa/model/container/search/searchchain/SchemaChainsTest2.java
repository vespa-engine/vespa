// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.vespa.model.builder.xml.dom.chains.search.DomSearchChainsBuilder;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilderTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author gjoranv
 */
public class SchemaChainsTest2 {

    private MockRoot root;

    @BeforeEach
    public void prepareTest() {
        root = new MockRoot("root");
    }

    @Test
    void fail_upon_unresolved_inheritance() {
        final Element searchElem = DomBuilderTest.parse(
                "<search>",
                "  <chain id='default' inherits='nonexistent' />",
                "</search>");
        try {
            MockRoot root = new MockRoot();
            SearchChains chains = new DomSearchChainsBuilder().build(root.getDeployState(), root, searchElem);
            chains.validate();
            fail("Expected exception when inheriting a nonexistent search chain.");
        } catch (Exception e) {
            assertEquals("Missing chain 'nonexistent'.",
                    e.getMessage());
        }
    }

    @Test
    void fail_upon_two_user_declared_chains_with_same_name() {
        final Element clusterElem = DomBuilderTest.parse(
                "<container id='cluster1' version='1.0'>",
                ContainerModelBuilderTest.nodesXml,
                "  <search>",
                "    <chain id='same' />",
                "    <chain id='same' />",
                "  </search>",
                "</container>");
        try {
            ContainerModelBuilderTest.createModel(root, clusterElem);
            fail("Expected exception when declaring chains with duplicate id.");
        } catch (Exception e) {
            assertEquals("Both search chain 'same' and search chain 'same' are configured with the id 'same'. All components must have a unique id.",
                    e.getMessage());
        }
    }

    @Test
    void fail_upon_user_declared_chain_with_same_id_as_builtin_chain() {
        final Element clusterElem = DomBuilderTest.parse(
                "<container id='cluster1' version='1.0'>",
                ContainerModelBuilderTest.nodesXml,
                "  <search>",
                "    <chain id='vespa' />",
                "  </search>",
                "</container>");
        try {
            ContainerModelBuilderTest.createModel(root, clusterElem);
            fail("Expected exception when taking the id from a builtin chain.");
        } catch (Exception e) {
            assertEquals("Both search chain 'vespa' and search chain 'vespa' are configured with the id 'vespa'. All components must have a unique id.",
                    e.getMessage());
        }
    }
}
