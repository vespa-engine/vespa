// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.google.common.collect.ImmutableSet;
import com.yahoo.collections.CollectionUtil;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.container.jdisc.state.StateHandler;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.http.AccessControl;
import com.yahoo.vespa.model.container.http.Http;
import org.junit.Test;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class AccessControlTest extends ContainerModelBuilderTestBase {

    private static final Set<String> requiredBindings = ImmutableSet.of(
            "/custom-handler/",
            "/search/",
            "/feed",
            "/remove",
            "/removelocation",
            "/get",
            "/visit",
            "/document",
            "/feedstatus",
            ContainerCluster.RESERVED_URI_PREFIX);

    private static final Set<String> forbiddenBindings = ImmutableSet.of(
            "/ApplicationStatus",
            "/status.html",
            "/statistics/",
            StateHandler.STATE_API_ROOT,
            ContainerCluster.ROOT_HANDLER_BINDING);

    @Test
    public void access_control_filter_chain_is_set_up() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>",
                "  <search/>",
                "  <document-api/>",
                "  <handler id='custom.Handler'>",
                "    <binding>http://*/custom-handler/*</binding>",
                "  </handler>",
                "  <http>",
                "    <filtering>",
                "      <access-control domain='foo' />",
                "    </filtering>",
                "  </http>",
                "</jdisc>");

        createModel(root, clusterElem);
        ContainerCluster cluster = (ContainerCluster) root.getChildren().get("default");
        Http http = cluster.getHttp();
        assertNotNull(http);

        assertTrue(http.getFilterChains().hasChain(AccessControl.ACCESS_CONTROL_CHAIN_ID));
    }

    @Test
    public void access_control_filter_chain_has_correct_handler_bindings() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>",
                "  <search/>",
                "  <document-api/>",
                "  <handler id='custom.Handler'>",
                "    <binding>http://*/custom-handler/*</binding>",
                "  </handler>",
                "  <http>",
                "    <filtering>",
                "      <access-control domain='foo' />",
                "    </filtering>",
                "  </http>",
                "</jdisc>");

        createModel(root, clusterElem);
        ContainerCluster cluster = (ContainerCluster) root.getChildren().get("default");
        Http http = cluster.getHttp();
        assertNotNull(http);

        Set<String> foundRequiredBindings = requiredBindings.stream()
                .filter(requiredBinding -> containsBinding(http.getBindings(), requiredBinding))
                .collect(Collectors.toSet());
        Set<String> missingRequiredBindings = new HashSet<>(requiredBindings);
        missingRequiredBindings.removeAll(foundRequiredBindings);
        assertTrue("Access control chain was not bound to: " + CollectionUtil.mkString(requiredBindings, ", "),
                   missingRequiredBindings.isEmpty());

        for (String forbiddenBinding : forbiddenBindings) {
            for (Http.Binding binding : http.getBindings())
                assertFalse("Access control chain was bound to: ",
                            binding.binding.contains(forbiddenBinding));
        }
    }

    private boolean containsBinding(Collection<Http.Binding> bindings, String binding) {
        for (Http.Binding b : bindings) {
            if (b.binding.contains(binding))
                return true;
        }
        return false;
    }

}
