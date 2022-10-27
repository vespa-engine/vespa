// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.path.Path;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.container.ApplicationContainer;
import com.yahoo.vespa.model.container.http.AccessControl;
import com.yahoo.vespa.model.container.http.ConnectorFactory;
import com.yahoo.vespa.model.container.http.FilterChains;
import com.yahoo.vespa.model.container.http.Http;
import com.yahoo.vespa.model.container.http.ssl.HostedSslConnectorFactory;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

import java.io.File;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author gjoranv
 * @author bjorncs
 * @author mortent
 */
public class AccessControlTest extends ContainerModelBuilderTestBase {

    @TempDir
    public File applicationFolder;

    @Test
    void access_control_filter_chains_are_set_up() {
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
    void properties_are_set_from_xml() {
        Http http = createModelAndGetHttp(
                "  <http>",
                "    <filtering>",
                "      <access-control domain='my-tenant-domain'/>",
                "    </filtering>",
                "  </http>");

        AccessControl accessControl = http.getAccessControl().get();

        assertEquals("my-tenant-domain", accessControl.domain, "Wrong domain.");
    }


    @Test
    void access_control_excluded_filter_chain_has_all_bindings_from_excluded_handlers() {
        Http http = createModelAndGetHttp(
                "  <http>",
                "    <filtering>",
                "      <access-control/>",
                "    </filtering>",
                "  </http>");

        Set<String> actualBindings = getFilterBindings(http, AccessControl.ACCESS_CONTROL_EXCLUDED_CHAIN_ID);
        assertTrue(actualBindings.containsAll(List.of(
                "http://*:4443/ApplicationStatus",
                "http://*:4443/status.html",
                "http://*:4443/state/v1",
                "http://*:4443/state/v1/*",
                "http://*:4443/prometheus/v1",
                "http://*:4443/prometheus/v1/*",
                "http://*:4443/metrics/v2",
                "http://*:4443/metrics/v2/*",
                "http://*:4443/")));
    }

    @Test
    void access_control_excluded_chain_does_not_contain_any_bindings_from_access_control_chain() {
        Http http = createModelAndGetHttp(
                "  <http>",
                "    <filtering>",
                "      <access-control/>",
                "    </filtering>",
                "  </http>");

        Set<String> bindings = getFilterBindings(http, AccessControl.ACCESS_CONTROL_CHAIN_ID);
        Set<String> excludedBindings = getFilterBindings(http, AccessControl.ACCESS_CONTROL_EXCLUDED_CHAIN_ID);

        for (String binding : bindings) {
            assertFalse(excludedBindings.contains(binding));
        }
    }


    @Test
    void access_control_excluded_filter_chain_has_user_provided_excluded_bindings() {
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
        assertTrue(actualBindings.containsAll(List.of("http://*:4443/custom-handler/*", "http://*:4443/search/*", "http://*:4443/status.html")));
    }

    @Test
    void hosted_connector_for_port_4443_uses_access_control_filter_chain_as_default_request_filter_chain() {
        Http http = createModelAndGetHttp(
                "  <http>",
                "    <filtering>",
                "      <access-control/>",
                "    </filtering>",
                "  </http>");

        Set<String> actualBindings = getFilterBindings(http, AccessControl.ACCESS_CONTROL_CHAIN_ID);
        assertTrue(actualBindings.isEmpty());

        HostedSslConnectorFactory hostedConnectorFactory = (HostedSslConnectorFactory) http.getHttpServer().get().getConnectorFactories().stream()
                .filter(connectorFactory -> connectorFactory instanceof HostedSslConnectorFactory)
                .findAny()
                .get();
        Optional<ComponentId> maybeDefaultChain = hostedConnectorFactory.getDefaultRequestFilterChain();
        assertTrue(maybeDefaultChain.isPresent());
        assertEquals(AccessControl.ACCESS_CONTROL_CHAIN_ID, maybeDefaultChain.get());
    }

    @Test
    void access_control_is_implicitly_added_for_hosted_apps() {
        Http http = createModelAndGetHttp("<container version='1.0'/>");
        Optional<AccessControl> maybeAccessControl = http.getAccessControl();
        assertTrue(maybeAccessControl.isPresent());
        AccessControl accessControl = maybeAccessControl.get();
        assertEquals(AccessControl.ClientAuthentication.need, accessControl.clientAuthentication);
        assertEquals("my-tenant-domain", accessControl.domain);
    }

    @Test
    void access_control_is_implicitly_added_for_hosted_apps_with_existing_http_element() {
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
        assertTrue(http.getAccessControl().isPresent());
        assertTrue(http.getFilterChains().hasChain(AccessControl.ACCESS_CONTROL_CHAIN_ID));
        assertTrue(http.getFilterChains().hasChain(ComponentId.fromString("myChain")));
    }

    @Test
    void access_control_chain_exclude_chain_does_not_contain_duplicate_bindings_to_user_request_filter_chain() {
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
        assertTrue(actualExcludeBindings.containsAll(List.of(
                "http://*:4443/ApplicationStatus",
                "http://*:4443/status.html",
                "http://*:4443/state/v1",
                "http://*:4443/state/v1/*",
                "http://*:4443/prometheus/v1",
                "http://*:4443/prometheus/v1/*",
                "http://*:4443/metrics/v2",
                "http://*:4443/metrics/v2/*")));

        Set<String> actualCustomChainBindings = getFilterBindings(http, ComponentId.fromString("my-custom-request-chain"));
        assertTrue(actualCustomChainBindings.containsAll(List.of("http://*/custom-handler/*", "http://*/")));
    }

    @Test
    void access_control_excludes_are_not_affected_by_user_response_filter_chain() {
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
        assertTrue(actualExcludeBindings.containsAll(List.of(
                "http://*:4443/ApplicationStatus",
                "http://*:4443/status.html",
                "http://*:4443/state/v1",
                "http://*:4443/state/v1/*",
                "http://*:4443/prometheus/v1",
                "http://*:4443/prometheus/v1/*",
                "http://*:4443/metrics/v2",
                "http://*:4443/metrics/v2/*",
                "http://*:4443/",
                "http://*:4443/custom-handler/*")));

        Set<String> actualCustomChainBindings = getFilterBindings(http, ComponentId.fromString("my-custom-response-chain"));
        assertTrue(actualCustomChainBindings.contains("http://*/custom-handler/*"));
    }

    @Test
    void access_control_client_auth_defaults_to_need() {
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
    void access_control_client_auth_can_be_overridden() {
        AthenzDomain tenantDomain = AthenzDomain.from("my-tenant-domain");
        DeployState state = new DeployState.Builder().properties(
                new TestProperties()
                        .setAthenzDomain(tenantDomain)
                        .setHostedVespa(true)
                        .allowDisableMtls(true))
                .build();
        Http http = createModelAndGetHttp(state,
                "  <http>",
                "    <filtering>",
                "      <access-control tls-handshake-client-auth=\"want\"/>",
                "    </filtering>",
                "  </http>");
        assertTrue(http.getAccessControl().isPresent());
        assertEquals(AccessControl.ClientAuthentication.want, http.getAccessControl().get().clientAuthentication);
    }

    @Test
    void access_control_client_auth_cannot_be_overridden_when_disabled() {
        AthenzDomain tenantDomain = AthenzDomain.from("my-tenant-domain");
        DeployState state = new DeployState.Builder().properties(
                new TestProperties()
                        .setAthenzDomain(tenantDomain)
                        .setHostedVespa(true)
                        .allowDisableMtls(false))
                .build();

        try {
            Http http = createModelAndGetHttp(state,
                    "  <http>",
                    "    <filtering>",
                    "      <access-control tls-handshake-client-auth=\"want\"/>",
                    "    </filtering>",
                    "  </http>");
            fail("Overriding tls-handshake-client-auth allowed, but should have failed");
        } catch (IllegalArgumentException e) {
            assertEquals("Overriding 'tls-handshake-client-auth' for application is not allowed.", e.getMessage());
        }
    }

    @Test
    void local_connector_has_default_chain() {
        Http http = createModelAndGetHttp(
                "  <http>",
                "    <filtering>",
                "      <access-control/>",
                "    </filtering>",
                "  </http>");

        Set<String> actualBindings = getFilterBindings(http, AccessControl.DEFAULT_CONNECTOR_HOSTED_REQUEST_CHAIN_ID);
        assertTrue(actualBindings.isEmpty());

        ConnectorFactory connectorFactory = http.getHttpServer().get().getConnectorFactories().stream()
                .filter(cf -> cf.getListenPort() == getDefaults().vespaWebServicePort())
                .findAny()
                .get();

        Optional<ComponentId> defaultChain = connectorFactory.getDefaultRequestFilterChain();
        assertTrue(defaultChain.isPresent());
        assertEquals(AccessControl.DEFAULT_CONNECTOR_HOSTED_REQUEST_CHAIN_ID, defaultChain.get());
    }

    @Test
    void client_authentication_is_enforced() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                nodesXml,
                "   <http><filtering>" +
                        "      <access-control domain=\"vespa\" tls-handshake-client-auth=\"need\"/>" +
                        "   </filtering></http>" +
                        "</container>");

        DeployState state = new DeployState.Builder().properties(
                new TestProperties()
                        .setHostedVespa(true)
                        .setEndpointCertificateSecrets(Optional.of(new EndpointCertificateSecrets("CERT", "KEY"))))
                .build();
        createModel(root, state, null, clusterElem);
        ApplicationContainer container = (ApplicationContainer) root.getProducer("container/container.0");

        List<ConnectorFactory> connectorFactories = container.getHttp().getHttpServer().get().getConnectorFactories();
        ConnectorFactory tlsPort = connectorFactories.stream().filter(connectorFactory -> connectorFactory.getListenPort() == 4443).findFirst().orElseThrow();

        ConnectorConfig.Builder builder = new ConnectorConfig.Builder();
        tlsPort.getConfig(builder);

        ConnectorConfig connectorConfig = new ConnectorConfig(builder);
        assertTrue(connectorConfig.ssl().enabled());
        assertEquals(ConnectorConfig.Ssl.ClientAuth.Enum.NEED_AUTH, connectorConfig.ssl().clientAuth());
        assertEquals("CERT", connectorConfig.ssl().certificate());
        assertEquals("KEY", connectorConfig.ssl().privateKey());
        assertEquals(4443, connectorConfig.listenPort());

        assertEquals("/opt/yahoo/share/ssl/certs/athenz_certificate_bundle.pem",
                connectorConfig.ssl().caCertificateFile(),
                "Connector must use Athenz truststore in a non-public system.");
        assertTrue(connectorConfig.ssl().caCertificate().isEmpty());
    }

    @Test
    void missing_security_clients_pem_fails_in_public() {
        Element clusterElem = DomBuilderTest.parse("<container version='1.0' />");

        try {
            DeployState state = new DeployState.Builder()
                    .properties(
                            new TestProperties()
                                    .setHostedVespa(true)
                                    .setEndpointCertificateSecrets(Optional.of(new EndpointCertificateSecrets("CERT", "KEY"))))
                    .zone(new Zone(SystemName.Public, Environment.prod, RegionName.defaultName()))
                    .build();
            createModel(root, state, null, clusterElem);
        } catch (RuntimeException e) {
            assertEquals("Client certificate authority security/clients.pem is missing - see: https://cloud.vespa.ai/en/security-model#data-plane",
                    e.getMessage());
            return;
        }
        fail();
    }

    @Test
    void security_clients_pem_is_picked_up() {
        var applicationPackage = new MockApplicationPackage.Builder()
                .withRoot(applicationFolder)
                .build();

        applicationPackage.getFile(Path.fromString("security")).createDirectory();
        applicationPackage.getFile(Path.fromString("security/clients.pem")).writeFile(new StringReader("I am a very nice certificate"));

        var deployState = DeployState.createTestState(applicationPackage);

        Element clusterElem = DomBuilderTest.parse("<container version='1.0' />");

        createModel(root, deployState, null, clusterElem);
        assertEquals(Optional.of("I am a very nice certificate"), getContainerCluster("container").getTlsClientAuthority());
    }

    @Test
    void operator_certificates_are_joined_with_clients_pem() {
        var applicationPackage = new MockApplicationPackage.Builder()
                .withRoot(applicationFolder)
                .build();

        var applicationTrustCert = X509CertificateUtils.toPem(
                X509CertificateUtils.createSelfSigned("CN=application", Duration.ofDays(1)).certificate());
        var operatorCert = X509CertificateUtils.createSelfSigned("CN=operator", Duration.ofDays(1)).certificate();

        applicationPackage.getFile(Path.fromString("security")).createDirectory();
        applicationPackage.getFile(Path.fromString("security/clients.pem")).writeFile(new StringReader(applicationTrustCert));

        var deployState = new DeployState.Builder().properties(
                new TestProperties()
                        .setOperatorCertificates(List.of(operatorCert))
                        .setHostedVespa(true)
                        .setEndpointCertificateSecrets(Optional.of(new EndpointCertificateSecrets("CERT", "KEY"))))
                .zone(new Zone(SystemName.PublicCd, Environment.dev, RegionName.defaultName()))
                .applicationPackage(applicationPackage)
                .build();

        Element clusterElem = DomBuilderTest.parse("<container version='1.0' />");

        createModel(root, deployState, null, clusterElem);

        ApplicationContainer container = (ApplicationContainer) root.getProducer("container/container.0");
        List<ConnectorFactory> connectorFactories = container.getHttp().getHttpServer().get().getConnectorFactories();
        ConnectorFactory tlsPort = connectorFactories.stream().filter(connectorFactory -> connectorFactory.getListenPort() == 4443).findFirst().orElseThrow();

        ConnectorConfig.Builder builder = new ConnectorConfig.Builder();
        tlsPort.getConfig(builder);

        ConnectorConfig connectorConfig = new ConnectorConfig(builder);
        var caCerts = X509CertificateUtils.certificateListFromPem(connectorConfig.ssl().caCertificate());
        assertEquals(2, caCerts.size());
        List<String> certnames = caCerts.stream()
                .map(cert -> cert.getSubjectX500Principal().getName())
                .collect(Collectors.toList());
        assertThat(certnames, containsInAnyOrder("CN=operator", "CN=application"));
    }

    @Test
    void require_allowed_ciphers() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                nodesXml,
                "</container>");

        DeployState state = new DeployState.Builder().properties(new TestProperties().setHostedVespa(true).setEndpointCertificateSecrets(Optional.of(new EndpointCertificateSecrets("CERT", "KEY")))).build();
        createModel(root, state, null, clusterElem);
        ApplicationContainer container = (ApplicationContainer) root.getProducer("container/container.0");

        List<ConnectorFactory> connectorFactories = container.getHttp().getHttpServer().get().getConnectorFactories();
        ConnectorFactory tlsPort = connectorFactories.stream().filter(connectorFactory -> connectorFactory.getListenPort() == 4443).findFirst().orElseThrow();
        ConnectorConfig.Builder builder = new ConnectorConfig.Builder();
        tlsPort.getConfig(builder);

        ConnectorConfig connectorConfig = new ConnectorConfig(builder);

        assertThat(connectorConfig.ssl().enabledCipherSuites(), containsInAnyOrder(TlsContext.ALLOWED_CIPHER_SUITES.toArray()));
    }

    @Test
    void providing_endpoint_certificate_secrets_opens_port_4443() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                nodesXml,
                "</container>");

        DeployState state = new DeployState.Builder().properties(new TestProperties().setHostedVespa(true).setEndpointCertificateSecrets(Optional.of(new EndpointCertificateSecrets("CERT", "KEY")))).build();
        createModel(root, state, null, clusterElem);
        ApplicationContainer container = (ApplicationContainer) root.getProducer("container/container.0");

        // Verify that there are two connectors
        List<ConnectorFactory> connectorFactories = container.getHttp().getHttpServer().get().getConnectorFactories();
        assertEquals(2, connectorFactories.size());
        List<Integer> ports = connectorFactories.stream()
                .map(ConnectorFactory::getListenPort)
                .collect(Collectors.toList());
        assertThat(ports, Matchers.containsInAnyOrder(8080, 4443));

        ConnectorFactory tlsPort = connectorFactories.stream().filter(connectorFactory -> connectorFactory.getListenPort() == 4443).findFirst().orElseThrow();

        ConnectorConfig.Builder builder = new ConnectorConfig.Builder();
        tlsPort.getConfig(builder);


        ConnectorConfig connectorConfig = new ConnectorConfig(builder);
        assertTrue(connectorConfig.ssl().enabled());
        assertEquals(ConnectorConfig.Ssl.ClientAuth.Enum.WANT_AUTH, connectorConfig.ssl().clientAuth());
        assertEquals("CERT", connectorConfig.ssl().certificate());
        assertEquals("KEY", connectorConfig.ssl().privateKey());
        assertEquals(4443, connectorConfig.listenPort());

        assertEquals("/opt/yahoo/share/ssl/certs/athenz_certificate_bundle.pem",
                connectorConfig.ssl().caCertificateFile(),
                "Connector must use Athenz truststore in a non-public system.");
        assertTrue(connectorConfig.ssl().caCertificate().isEmpty());
    }

    private Http createModelAndGetHttp(String... httpElement) {
        AthenzDomain tenantDomain = AthenzDomain.from("my-tenant-domain");
        DeployState state = new DeployState.Builder().properties(
                new TestProperties()
                        .setAthenzDomain(tenantDomain)
                        .setHostedVespa(true))
                .build();
        return createModelAndGetHttp(state, httpElement);
    }

    private Http createModelAndGetHttp(DeployState state, String... httpElement) {
        List<String> servicesXml = new ArrayList<>();
        servicesXml.add("<container version='1.0'>");
        servicesXml.addAll(List.of(httpElement));
        servicesXml.add("</container>");

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
