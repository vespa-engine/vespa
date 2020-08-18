// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.vespa.model.container.ApplicationContainer;
import com.yahoo.vespa.model.container.http.AccessControl;
import com.yahoo.vespa.model.container.http.FilterChains;
import com.yahoo.vespa.model.container.http.Http;
import org.junit.Test;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
                "<container version='1.0'>",
                "  <http>",
                "    <filtering>",
                "      <access-control domain='my-tenant-domain' />",
                "    </filtering>",
                "  </http>",
                "</container>");

        FilterChains filterChains = http.getFilterChains();
        assertTrue(filterChains.hasChain(AccessControl.ACCESS_CONTROL_CHAIN_ID));
        assertTrue(filterChains.hasChain(AccessControl.ACCESS_CONTROL_EXCLUDED_CHAIN_ID));
    }

    @Test
    public void properties_are_set_from_xml() {
        Http http = createModelAndGetHttp(
                "<container version='1.0'>",
                "  <http>",
                "    <filtering>",
                "      <access-control domain='my-tenant-domain'/>",
                "    </filtering>",
                "  </http>",
                "</container>");

        AccessControl accessControl = http.getAccessControl().get();

        assertEquals("Wrong domain.", "my-tenant-domain", accessControl.domain);
    }

    @Test
    public void read_is_disabled_and_write_is_enabled_by_default() {
        Http http = createModelAndGetHttp(
                "<container version='1.0'>",
                "  <http>",
                "    <filtering>",
                "      <access-control domain='my-tenant-domain'/>",
                "    </filtering>",
                "  </http>",
                "</container>");

        assertFalse("Wrong default value for read.", http.getAccessControl().get().readEnabled);
        assertTrue("Wrong default value for write.", http.getAccessControl().get().writeEnabled);
    }

    @Test
    public void read_and_write_can_be_overridden() {
        Http http = createModelAndGetHttp(
                "<container version='1.0'>",
                "  <http>",
                "    <filtering>",
                "      <access-control domain='my-tenant-domain' read='true' write='false'/>",
                "    </filtering>",
                "  </http>",
                "</container>");

        assertTrue("Given read value not honoured.", http.getAccessControl().get().readEnabled);
        assertFalse("Given write value not honoured.", http.getAccessControl().get().writeEnabled);
    }

    @Test
    public void access_control_excluded_filter_chain_has_all_bindings_from_excluded_handlers() {
        Http http = createModelAndGetHttp(
                "<container version='1.0'>",
                "  <http>",
                "    <filtering>",
                "      <access-control/>",
                "    </filtering>",
                "  </http>",
                "</container>");

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
                "<container version='1.0'>",
                "  <http>",
                "    <filtering>",
                "      <access-control/>",
                "    </filtering>",
                "  </http>",
                "</container>");

        Set<String> bindings = getFilterBindings(http, AccessControl.ACCESS_CONTROL_CHAIN_ID);
        Set<String> excludedBindings = getFilterBindings(http, AccessControl.ACCESS_CONTROL_EXCLUDED_CHAIN_ID);

        for (String binding : bindings) {
            assertThat(excludedBindings, not(hasItem(binding)));
        }
    }


    @Test
    public void access_control_excluded_filter_chain_has_user_provided_excluded_bindings() {
        Http http = createModelAndGetHttp(
                "<container version='1.0'>",
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
                "  </http>",
                "</container>");

        Set<String> actualBindings = getFilterBindings(http, AccessControl.ACCESS_CONTROL_EXCLUDED_CHAIN_ID);
        assertThat(actualBindings, hasItems("http://*:4443/custom-handler/*", "http://*:4443/search/*", "http://*:4443/status.html"));
    }

    @Test
    public void access_control_filter_chain_contains_catchall_bindings() {
        Http http = createModelAndGetHttp(
                "<container version='1.0'>",
                "  <http>",
                "    <filtering>",
                "      <access-control/>",
                "    </filtering>",
                "  </http>",
                "</container>");
        Set<String> actualBindings = getFilterBindings(http, AccessControl.ACCESS_CONTROL_CHAIN_ID);
        assertThat(actualBindings, containsInAnyOrder("http://*:4443/*"));
    }

    @Test
    public void access_control_is_implicitly_added_for_hosted_apps() {
        Http http = createModelAndGetHttp("<container version='1.0'/>");
        Optional<AccessControl> maybeAccessControl = http.getAccessControl();
        assertThat(maybeAccessControl.isPresent(), is(true));
        AccessControl accessControl = maybeAccessControl.get();
        assertThat(accessControl.writeEnabled, is(false));
        assertThat(accessControl.readEnabled, is(false));
        assertThat(accessControl.domain, equalTo("my-tenant-domain"));
    }

    @Test
    public void access_control_is_implicitly_added_for_hosted_apps_with_existing_http_element() {
        Http http = createModelAndGetHttp(
                "<container version='1.0'>",
                "  <http>",
                "    <server port='" + getDefaults().vespaWebServicePort() + "' id='main' />",
                "    <filtering>",
                "      <filter id='outer' />",
                "      <request-chain id='myChain'>",
                "        <filter id='inner' />",
                "      </request-chain>",
                "    </filtering>",
                "  </http>",
                "</container>" );
        assertThat(http.getAccessControl().isPresent(), is(true));
        assertThat(http.getFilterChains().hasChain(AccessControl.ACCESS_CONTROL_CHAIN_ID), is(true));
        assertThat(http.getFilterChains().hasChain(ComponentId.fromString("myChain")), is(true));
    }

    private Http createModelAndGetHttp(String... servicesXml) {
        AthenzDomain tenantDomain = AthenzDomain.from("my-tenant-domain");
        DeployState state = new DeployState.Builder().properties(
                new TestProperties()
                        .setAthenzDomain(tenantDomain)
                        .setHostedVespa(true))
                .build();
        createModel(root, state, null, DomBuilderTest.parse(servicesXml));
        return  ((ApplicationContainer) root.getProducer("container/container.0")).getHttp();
    }

    private static Set<String> getFilterBindings(Http http, ComponentId filerChain) {
        return http.getBindings().stream()
                .filter(binding -> binding.filterId().toId().equals(filerChain))
                .map(binding -> binding.binding().patternString())
                .collect(Collectors.toSet());
    }

}
