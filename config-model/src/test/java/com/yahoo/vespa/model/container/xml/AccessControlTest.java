// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.container.ApplicationContainer;
import com.yahoo.vespa.model.container.http.AccessControl;
import com.yahoo.vespa.model.container.http.ConnectorFactory;
import com.yahoo.vespa.model.container.http.FilterChains;
import com.yahoo.vespa.model.container.http.Http;
import com.yahoo.vespa.model.container.http.ssl.HostedSslConnectorFactory;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 * @author bjorncs
 */
public class AccessControlTest extends ContainerModelBuilderTestBase {

    @Test
    public void access_control_filter_chains_are_set_up() {
        Http http = createModelAndGetHttp(
                "  <http>",
                "    <filtering>",
                "      <access-control domain='my-tenant-domain' />",
                "    </filtering>",
                "  </http>");

        FilterChains filterChains = http.getFilterChains();
        assertTrue(filterChains.hasChain(AccessControl.ACCESS_CONTROL_CHAIN_ID));
        assertTrue(filterChains.hasChain(AccessControl.ACCESS_CONTROL_EXCLUDED_CHAIN_ID));
        assertTrue(filterChains.hasChain(AccessControl.DEFAULT_CONNECTOR_HOSTED_REQUEST_CHAIN_ID));
    }

    @Test
    public void properties_are_set_from_xml() {
        Http http = createModelAndGetHttp(
                "  <http>",
                "    <filtering>",
                "      <access-control domain='my-tenant-domain'/>",
                "    </filtering>",
                "  </http>");

        AccessControl accessControl = http.getAccessControl().get();

        assertEquals("Wrong domain.", "my-tenant-domain", accessControl.domain);
    }

    @Test
    public void read_is_disabled_and_write_is_enabled_by_default() {
        Http http = createModelAndGetHttp(
                "  <http>",
                "    <filtering>",
                "      <access-control domain='my-tenant-domain'/>",
                "    </filtering>",
                "  </http>");

        assertFalse("Wrong default value for read.", http.getAccessControl().get().readEnabled);
        assertTrue("Wrong default value for write.", http.getAccessControl().get().writeEnabled);
    }

    @Test
    public void read_and_write_can_be_overridden() {
        Http http = createModelAndGetHttp(
                "  <http>",
                "    <filtering>",
                "      <access-control domain='my-tenant-domain' read='true' write='false'/>",
                "    </filtering>",
                "  </http>");

        assertTrue("Given read value not honoured.", http.getAccessControl().get().readEnabled);
        assertFalse("Given write value not honoured.", http.getAccessControl().get().writeEnabled);
    }

    @Test
    public void access_control_excluded_filter_chain_has_all_bindings_from_excluded_handlers() {
        Http http = createModelAndGetHttp(
                "  <http>",
                "    <filtering>",
                "      <access-control/>",
                "    </filtering>",
                "  </http>");

        Set<String> actualBindings = getFilterBindings(http, AccessControl.ACCESS_CONTROL_EXCLUDED_CHAIN_ID);
        assertThat(actualBindings, containsInAnyOrder(
                "http://*:4443/ApplicationStatus",
                "http://*:4443/status.html",
                "http://*:4443/state/v1",
                "http://*:4443/state/v1/*",
                "http://*:4443/prometheus/v1",
                "http://*:4443/prometheus/v1/*",
                "http://*:4443/metrics/v2",
                "http://*:4443/metrics/v2/*",
                "http://*:4443/"));
    }

    @Test
    public void access_control_excluded_chain_does_not_contain_any_bindings_from_access_control_chain() {
        Http http = createModelAndGetHttp(
                "  <http>",
                "    <filtering>",
                "      <access-control/>",
                "    </filtering>",
                "  </http>");

        Set<String> bindings = getFilterBindings(http, AccessControl.ACCESS_CONTROL_CHAIN_ID);
        Set<String> excludedBindings = getFilterBindings(http, AccessControl.ACCESS_CONTROL_EXCLUDED_CHAIN_ID);

        for (String binding : bindings) {
            assertThat(excludedBindings, not(hasItem(binding)));
        }
    }


    @Test
    public void access_control_excluded_filter_chain_has_user_provided_excluded_bindings() {
        Http http = createModelAndGetHttp(
                "  <http>",
                "    <handler id='custom.Handler'>",
                "      <binding>http://*/custom-handler/*</binding>",
                "    </handler>",
                "    <filtering>",
                "      <access-control>",
                "        <exclude>",
                "          <binding>http://*/custom-handler/*</binding>",
                "          <binding>http://*/search/*</binding>",
                "        </exclude>",
                "      </access-control>",
                "    </filtering>",
                "  </http>");

        Set<String> actualBindings = getFilterBindings(http, AccessControl.ACCESS_CONTROL_EXCLUDED_CHAIN_ID);
        assertThat(actualBindings, hasItems("http://*:4443/custom-handler/*", "http://*:4443/search/*", "http://*:4443/status.html"));
    }

    @Test
    public void hosted_connector_for_port_4443_uses_access_control_filter_chain_as_default_request_filter_chain() {
        Http http = createModelAndGetHttp(
                "  <http>",
                "    <filtering>",
                "      <access-control/>",
                "    </filtering>",
                "  </http>");

        Set<String> actualBindings = getFilterBindings(http, AccessControl.ACCESS_CONTROL_CHAIN_ID);
        assertThat(actualBindings, empty());

        HostedSslConnectorFactory hostedConnectorFactory = (HostedSslConnectorFactory)http.getHttpServer().get().getConnectorFactories().stream()
                .filter(connectorFactory -> connectorFactory instanceof HostedSslConnectorFactory)
                .findAny()
                .get();
        Optional<ComponentId> maybeDefaultChain = hostedConnectorFactory.getDefaultRequestFilterChain();
        assertTrue(maybeDefaultChain.isPresent());
        assertEquals(AccessControl.ACCESS_CONTROL_CHAIN_ID, maybeDefaultChain.get());
    }

    @Test
    public void access_control_is_implicitly_added_for_hosted_apps() {
        Http http = createModelAndGetHttp("<container version='1.0'/>");
        Optional<AccessControl> maybeAccessControl = http.getAccessControl();
        assertThat(maybeAccessControl.isPresent(), is(true));
        AccessControl accessControl = maybeAccessControl.get();
        assertThat(accessControl.writeEnabled, is(false));
        assertThat(accessControl.readEnabled, is(false));
        assertThat(accessControl.clientAuthentication, is(AccessControl.ClientAuthentication.need));
        assertThat(accessControl.domain, equalTo("my-tenant-domain"));
    }

    @Test
    public void access_control_is_implicitly_added_for_hosted_apps_with_existing_http_element() {
        Http http = createModelAndGetHttp(
                "  <http>",
                "    <server port='" + getDefaults().vespaWebServicePort() + "' id='main' />",
                "    <filtering>",
                "      <filter id='outer' />",
                "      <request-chain id='myChain'>",
                "        <filter id='inner' />",
                "      </request-chain>",
                "    </filtering>",
                "  </http>");
        assertThat(http.getAccessControl().isPresent(), is(true));
        assertThat(http.getFilterChains().hasChain(AccessControl.ACCESS_CONTROL_CHAIN_ID), is(true));
        assertThat(http.getFilterChains().hasChain(ComponentId.fromString("myChain")), is(true));
    }

    @Test
    public void access_control_chain_exclude_chain_does_not_contain_duplicate_bindings_to_user_request_filter_chain() {
        Http http = createModelAndGetHttp(
                "  <http>",
                "    <handler id='custom.Handler'>",
                "      <binding>http://*/custom-handler/*</binding>",
                "      <binding>http://*/</binding>",
                "    </handler>",
                "    <filtering>",
                "      <access-control/>",
                "      <request-chain id='my-custom-request-chain'>",
                "        <filter id='my-custom-request-filter' />",
                "        <binding>http://*/custom-handler/*</binding>",
                "        <binding>http://*/</binding>",
                "      </request-chain>",
                "    </filtering>",
                "  </http>");

        Set<String> actualExcludeBindings = getFilterBindings(http, AccessControl.ACCESS_CONTROL_EXCLUDED_CHAIN_ID);
        assertThat(actualExcludeBindings, containsInAnyOrder(
                "http://*:4443/ApplicationStatus",
                "http://*:4443/status.html",
                "http://*:4443/state/v1",
                "http://*:4443/state/v1/*",
                "http://*:4443/prometheus/v1",
                "http://*:4443/prometheus/v1/*",
                "http://*:4443/metrics/v2",
                "http://*:4443/metrics/v2/*"));

        Set<String> actualCustomChainBindings = getFilterBindings(http, ComponentId.fromString("my-custom-request-chain"));
        assertThat(actualCustomChainBindings, containsInAnyOrder("http://*/custom-handler/*", "http://*/"));
    }

    @Test
    public void access_control_excludes_are_not_affected_by_user_response_filter_chain() {
        Http http = createModelAndGetHttp(
                "  <http>",
                "    <handler id='custom.Handler'>",
                "      <binding>http://*/custom-handler/*</binding>",
                "    </handler>",
                "    <filtering>",
                "      <access-control>",
                "        <exclude>",
                "          <binding>http://*/custom-handler/*</binding>",
                "        </exclude>",
                "      </access-control>",
                "      <response-chain id='my-custom-response-chain'>",
                "        <filter id='my-custom-response-filter' />",
                "        <binding>http://*/custom-handler/*</binding>",
                "      </response-chain>",
                "    </filtering>",
                "  </http>");

        Set<String> actualExcludeBindings = getFilterBindings(http, AccessControl.ACCESS_CONTROL_EXCLUDED_CHAIN_ID);
        assertThat(actualExcludeBindings, containsInAnyOrder(
                "http://*:4443/ApplicationStatus",
                "http://*:4443/status.html",
                "http://*:4443/state/v1",
                "http://*:4443/state/v1/*",
                "http://*:4443/prometheus/v1",
                "http://*:4443/prometheus/v1/*",
                "http://*:4443/metrics/v2",
                "http://*:4443/metrics/v2/*",
                "http://*:4443/",
                "http://*:4443/custom-handler/*"));

        Set<String> actualCustomChainBindings = getFilterBindings(http, ComponentId.fromString("my-custom-response-chain"));
        assertThat(actualCustomChainBindings, containsInAnyOrder("http://*/custom-handler/*"));
    }

    @Test
    public void access_control_client_auth_defaults_to_need() {
        Http http = createModelAndGetHttp(
                "  <http>",
                "    <filtering>",
                "      <access-control />",
                "    </filtering>",
                "  </http>");
        assertTrue(http.getAccessControl().isPresent());
        assertEquals(AccessControl.ClientAuthentication.need, http.getAccessControl().get().clientAuthentication);
    }

    @Test
    public void access_control_client_auth_can_be_overridden() {
        Http http = createModelAndGetHttp(
                "  <http>",
                "    <filtering>",
                "      <access-control tls-handshake-client-auth=\"want\"/>",
                "    </filtering>",
                "  </http>");
        assertTrue(http.getAccessControl().isPresent());
        assertEquals(AccessControl.ClientAuthentication.want, http.getAccessControl().get().clientAuthentication);
    }

    @Test
    public void local_connector_has_default_chain() {
        Http http = createModelAndGetHttp(
                "  <http>",
                "    <filtering>",
                "      <access-control/>",
                "    </filtering>",
                "  </http>");

        Set<String> actualBindings = getFilterBindings(http, AccessControl.DEFAULT_CONNECTOR_HOSTED_REQUEST_CHAIN_ID);
        assertThat(actualBindings, empty());

        ConnectorFactory connectorFactory = http.getHttpServer().get().getConnectorFactories().stream()
                .filter(cf -> cf.getListenPort() == Defaults.getDefaults().vespaWebServicePort())
                .findAny()
                .get();

        Optional<ComponentId> defaultChain = connectorFactory.getDefaultRequestFilterChain();
        assertTrue(defaultChain.isPresent());
        assertEquals(AccessControl.DEFAULT_CONNECTOR_HOSTED_REQUEST_CHAIN_ID, defaultChain.get());
    }

    private Http createModelAndGetHttp(String... httpElement) {
        List<String> servicesXml = new ArrayList<>();
        servicesXml.add("<container version='1.0'>");
        servicesXml.addAll(List.of(httpElement));
        servicesXml.add("</container>");

        AthenzDomain tenantDomain = AthenzDomain.from("my-tenant-domain");
        DeployState state = new DeployState.Builder().properties(
                new TestProperties()
                        .setAthenzDomain(tenantDomain)
                        .setHostedVespa(true))
                .build();
        createModel(root, state, null, DomBuilderTest.parse(servicesXml.toArray(String[]::new)));
        return  ((ApplicationContainer) root.getProducer("container/container.0")).getHttp();
    }

    private static Set<String> getFilterBindings(Http http, ComponentId filerChain) {
        return http.getBindings().stream()
                .filter(binding -> binding.chainId().toId().equals(filerChain))
                .map(binding -> binding.binding().patternString())
                .collect(Collectors.toSet());
    }

}
