// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.google.common.collect.ImmutableSet;
import com.yahoo.collections.CollectionUtil;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.container.jdisc.state.StateHandler;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.http.Http;
import com.yahoo.vespa.model.container.http.Http.Binding;
import com.yahoo.vespa.model.container.http.xml.HttpBuilder;
import org.junit.Test;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class AccessControlTest extends ContainerModelBuilderTestBase {

    private static final Set<String> REQUIRED_BINDINGS = ImmutableSet.of(
            "/custom-handler/",
            "/search/",
            "/feed/",
            "/remove/",
            "/removelocation/",
            "/get/",
            "/visit/",
            "/document/",
            "/feedstatus/",
            ContainerCluster.RESERVED_URI_PREFIX);

    private static final Set<String> FORBIDDEN_BINDINGS = ImmutableSet.of(
            "/ApplicationStatus",
            "/status.html",
            "/statistics/",
            StateHandler.STATE_API_ROOT,
            ContainerCluster.ROOT_HANDLER_BINDING);

    @Test
    public void properties_are_set_from_xml() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "  <http>",
                "    <filtering>",
                "      <access-control domain='my-domain'>",
                "        <application>my-app</application>",
                "        <vespa-domain>custom-vespa-domain</vespa-domain>",
                "      </access-control>",
                "    </filtering>",
                "  </http>");

        Http http = new HttpBuilder().build(root, clusterElem);
        root.freezeModelTopology();

        assertEquals("Wrong domain.", "my-domain", http.getAccessControl().get().domain);
        assertEquals("Wrong application.", "my-app", http.getAccessControl().get().applicationId);
        assertEquals("Wrong vespa-domain.", "custom-vespa-domain", http.getAccessControl().get().vespaDomain);
    }

    @Test
    public void read_is_disabled_and_write_is_enabled_by_default() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "  <http>",
                "    <filtering>",
                "      <access-control domain='foo' />",
                "    </filtering>",
                "  </http>");

        Http http = new HttpBuilder().build(root, clusterElem);
        root.freezeModelTopology();

        assertFalse("Wrong default value for read.", http.getAccessControl().get().readEnabled);
        assertTrue("Wrong default value for write.", http.getAccessControl().get().writeEnabled);
    }

    @Test
    public void read_and_write_can_be_overridden() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "  <http>",
                "    <filtering>",
                "      <access-control domain='foo' read='true' write='false'/>",
                "    </filtering>",
                "  </http>");

        Http http = new HttpBuilder().build(root, clusterElem);
        root.freezeModelTopology();

        assertTrue("Given read value not honoured.", http.getAccessControl().get().readEnabled);
        assertFalse("Given write value not honoured.", http.getAccessControl().get().writeEnabled);
    }

    @Test
    public void access_control_filter_chain_has_correct_handler_bindings() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc version='1.0'>",
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

        Http http = getHttp(clusterElem);

        Set<String> foundRequiredBindings = REQUIRED_BINDINGS.stream()
                .filter(requiredBinding -> containsBinding(http.getBindings(), requiredBinding))
                .collect(Collectors.toSet());
        Set<String> missingRequiredBindings = new HashSet<>(REQUIRED_BINDINGS);
        missingRequiredBindings.removeAll(foundRequiredBindings);
        assertTrue("Access control chain was not bound to: " + CollectionUtil.mkString(missingRequiredBindings, ", "),
                   missingRequiredBindings.isEmpty());

        FORBIDDEN_BINDINGS.forEach(forbiddenBinding -> http.getBindings().forEach(
                binding -> assertFalse("Access control chain was bound to: " + binding.binding,
                                       binding.binding.contains(forbiddenBinding))));
    }

    @Test
    public void handler_can_be_excluded_by_excluding_one_of_its_bindings() throws Exception {
        final String notExcludedBinding = "http://*/custom-handler/*";
        final String excludedBinding = "http://*/excluded/*";
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc version='1.0'>",
                "  <handler id='custom.Handler'>",
                "    <binding>" + notExcludedBinding + "</binding>",
                "    <binding>" + excludedBinding + "</binding>",
                "  </handler>",
                "  <http>",
                "    <filtering>",
                "      <access-control domain='foo'>",
                "        <exclude>",
                "          <binding>" + excludedBinding + "</binding>",
                "        </exclude>",
                "      </access-control>",
                "    </filtering>",
                "  </http>",
                "</jdisc>");

        Http http = getHttp(clusterElem);
        assertFalse("Excluded binding was not removed.",
                    containsBinding(http.getBindings(), excludedBinding));
        assertFalse("Not all bindings of an excluded handler was removed.",
                    containsBinding(http.getBindings(), notExcludedBinding));

    }

    private Http getHttp(Element clusterElem) throws SAXException, IOException {
        createModel(root, clusterElem);
        ContainerCluster cluster = (ContainerCluster) root.getChildren().get("jdisc");
        Http http = cluster.getHttp();
        assertNotNull(http);
        return http;
    }

    private boolean containsBinding(Collection<Binding> bindings, String binding) {
        for (Binding b : bindings) {
            if (b.binding.contains(binding))
                return true;
        }
        return false;
    }

}
