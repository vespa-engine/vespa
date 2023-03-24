// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.provision.HostsXmlProvisioner;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.jdisc.FilterBindingsProvider;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import com.yahoo.vespa.model.container.http.ConnectorFactory;
import com.yahoo.vespa.model.container.http.JettyHttpServer;
import com.yahoo.vespa.model.container.http.ssl.ConfiguredFilebasedSslProvider;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.io.StringReader;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author einarmr
 * @author mortent
 */
public class JettyContainerModelBuilderTest extends ContainerModelBuilderTestBase {

    @Test
    void verify_that_overriding_connector_options_works() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>\n" +
                        "  <http>\n" +
                        "    <server id='bananarama' port='4321'>\n" +
                        "      <config name='jdisc.http.connector'>\n" +
                        "        <requestHeaderSize>300000</requestHeaderSize>\n" +
                        "        <headerCacheSize>300000</headerCacheSize>\n" +
                        "      </config>\n" +
                        "    </server>\n" +
                        "  </http>\n" +
                        nodesXml +
                        "</container>\n"
        );
        createModel(root, clusterElem);
        ConnectorConfig cfg = root.getConfig(ConnectorConfig.class, "default/http/jdisc-jetty/bananarama");
        assertEquals(300000, cfg.requestHeaderSize());
        assertEquals(300000, cfg.headerCacheSize());
    }

    @Test
    void verify_that_enabling_jetty_works() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>" +
                        nodesXml +
                        "</container>"
        );
        createModel(root, clusterElem);
        assertJettyServerInConfig();
    }

    @Test
    void verify_that_enabling_jetty_works_for_custom_http_servers() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <http>",
                "    <server port='9000' id='foo' />",
                "  </http>",
                nodesXml,
                "</container>");
        createModel(root, clusterElem);
        assertJettyServerInConfig();
    }

    @Test
    void verifyThatJettyHttpServerHasFilterBindingsProvider() {
        final Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                nodesXml,
                "</container>");
        createModel(root, clusterElem);

        final ComponentsConfig.Components jettyHttpServerComponent = extractComponentByClassName(
                containerComponentsConfig(), com.yahoo.jdisc.http.server.jetty.JettyHttpServer.class.getName());
        assertNotNull(jettyHttpServerComponent);

        final ComponentsConfig.Components filterBindingsProviderComponent = extractComponentByClassName(
                containerComponentsConfig(), FilterBindingsProvider.class.getName());
        assertNotNull(filterBindingsProviderComponent);

        final ComponentsConfig.Components.Inject filterBindingsProviderInjection = extractInjectionById(
                jettyHttpServerComponent, filterBindingsProviderComponent.id());
        assertNotNull(filterBindingsProviderInjection);
    }

    @Test
    void verifyThatJettyHttpServerHasFilterBindingsProviderForCustomHttpServers() {
        final Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <http>",
                "    <server port='9000' id='foo' />",
                "  </http>",
                nodesXml,
                "</container>");
        createModel(root, clusterElem);

        final ComponentsConfig.Components jettyHttpServerComponent = extractComponentByClassName(
                clusterComponentsConfig(), com.yahoo.jdisc.http.server.jetty.JettyHttpServer.class.getName());
        assertNotNull(jettyHttpServerComponent);

        final ComponentsConfig.Components filterBindingsProviderComponent = extractComponentByClassName(
                clusterComponentsConfig(), FilterBindingsProvider.class.getName());
        assertNotNull(filterBindingsProviderComponent);

        final ComponentsConfig.Components.Inject filterBindingsProviderInjection = extractInjectionById(
                jettyHttpServerComponent, filterBindingsProviderComponent.id());
        assertNotNull(filterBindingsProviderInjection);
    }

    @Test
    void ssl_element_generates_connector_config_and_injects_provider_component() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "    <http>",
                "        <server port='9000' id='minimal'>",
                "            <ssl>",
                "                <private-key-file>/foo/key</private-key-file>",
                "                <certificate-file>/foo/cert</certificate-file>",
                "            </ssl>",
                "        </server>",
                "        <server port='9001' id='with-cacerts'>",
                "            <ssl>",
                "                <private-key-file>/foo/key</private-key-file>",
                "                <certificate-file>/foo/cert</certificate-file>",
                "                <ca-certificates-file>/foo/cacerts</ca-certificates-file>",
                "            </ssl>",
                "        </server>",
                "        <server port='9002' id='need-client-auth'>",
                "            <ssl>",
                "                <private-key-file>/foo/key</private-key-file>",
                "                <certificate-file>/foo/cert</certificate-file>",
                "                <client-authentication>need</client-authentication>",
                "            </ssl>",
                "        </server>",
                "        <server port='9003' id='with-ciphers-and-protocols'>",
                "            <ssl>",
                "                <private-key-file>/foo/key</private-key-file>",
                "                <certificate-file>/foo/cert</certificate-file>",
                "                <cipher-suites>TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384</cipher-suites>",
                "                <protocols>TLSv1.3</protocols>",
                "            </ssl>",
                "        </server>",
                "    </http>",
                nodesXml,
                "",
                "</container>");

        createModel(root, clusterElem);
        ConnectorConfig minimalCfg = root.getConfig(ConnectorConfig.class, "default/http/jdisc-jetty/minimal/configured-ssl-provider@minimal");
        assertTrue(minimalCfg.ssl().enabled());
        assertEquals("/foo/key", minimalCfg.ssl().privateKeyFile());
        assertEquals("/foo/cert", minimalCfg.ssl().certificateFile());
        assertTrue(minimalCfg.ssl().caCertificateFile().isEmpty());
        assertEquals(ConnectorConfig.Ssl.ClientAuth.Enum.DISABLED, minimalCfg.ssl().clientAuth());

        ConnectorConfig withCaCerts = root.getConfig(ConnectorConfig.class, "default/http/jdisc-jetty/with-cacerts/configured-ssl-provider@with-cacerts");
        assertTrue(withCaCerts.ssl().enabled());
        assertEquals("/foo/key", withCaCerts.ssl().privateKeyFile());
        assertEquals("/foo/cert", withCaCerts.ssl().certificateFile());
        assertEquals("/foo/cacerts", withCaCerts.ssl().caCertificateFile());
        assertEquals(ConnectorConfig.Ssl.ClientAuth.Enum.DISABLED, withCaCerts.ssl().clientAuth());

        ConnectorConfig needClientAuth = root.getConfig(ConnectorConfig.class, "default/http/jdisc-jetty/need-client-auth/configured-ssl-provider@need-client-auth");
        assertTrue(needClientAuth.ssl().enabled());
        assertEquals("/foo/key", needClientAuth.ssl().privateKeyFile());
        assertEquals("/foo/cert", needClientAuth.ssl().certificateFile());
        assertTrue(needClientAuth.ssl().caCertificateFile().isEmpty());
        assertEquals(ConnectorConfig.Ssl.ClientAuth.Enum.NEED_AUTH, needClientAuth.ssl().clientAuth());

        ConnectorConfig withCiphersAndProtocols = root.getConfig(ConnectorConfig.class, "default/http/jdisc-jetty/with-ciphers-and-protocols/configured-ssl-provider@with-ciphers-and-protocols");
        assertTrue(withCiphersAndProtocols.ssl().enabled());
        assertEquals("/foo/key", withCiphersAndProtocols.ssl().privateKeyFile());
        assertEquals("/foo/cert", withCiphersAndProtocols.ssl().certificateFile());
        assertEquals(List.of("TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384"), withCiphersAndProtocols.ssl().enabledCipherSuites());
        assertEquals(List.of("TLSv1.3"), withCiphersAndProtocols.ssl().enabledProtocols());

        ContainerCluster<?> cluster = (ContainerCluster<?>) root.getChildren().get("default");
        List<ConnectorFactory> connectorFactories = cluster.getChildrenByTypeRecursive(ConnectorFactory.class);
        connectorFactories.forEach(connectorFactory -> assertChildComponentExists(connectorFactory, ConfiguredFilebasedSslProvider.COMPONENT_CLASS));
    }

    @Test
    void verify_tht_ssl_provider_configuration_configures_correct_config() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "    <http>",
                "        <server port='9000' id='ssl'>",
                "            <ssl-provider class='com.yahoo.CustomSslProvider' bundle='mybundle'/>",
                "        </server>",
                "    </http>",
                nodesXml,
                "",
                "</container>");

        createModel(root, clusterElem);
        ConnectorConfig sslProvider = root.getConfig(ConnectorConfig.class, "default/http/jdisc-jetty/ssl/ssl-provider@ssl");

        assertTrue(sslProvider.ssl().enabled());

        ContainerCluster<?> cluster = (ContainerCluster<?>) root.getChildren().get("default");
        List<ConnectorFactory> connectorFactories = cluster.getChildrenByTypeRecursive(ConnectorFactory.class);
        ConnectorFactory connectorFactory = connectorFactories.get(0);
        assertChildComponentExists(connectorFactory, "com.yahoo.CustomSslProvider");
    }

    @Test
    void verify_that_container_factory_sees_same_config() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "    <http>",
                "        <server port='9000' id='ssl'>",
                "            <ssl>",
                "                <private-key-file>/foo/key</private-key-file>",
                "                <certificate-file>/foo/cert</certificate-file>",
                "            </ssl>",
                "        </server>",
                "    </http>",
                nodesXml,
                "",
                "</container>");

        createModel(root, clusterElem);
        ConnectorConfig sslProvider = root.getConfig(ConnectorConfig.class, "default/http/jdisc-jetty/ssl");
        assertTrue(sslProvider.ssl().enabled());
    }

    @Test
    void verify_that_container_setup_additional_tls4443() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "    <http>",
                "        <server port='8080' id='default'>",
                "        </server>",
                "    </http>",
                multiNode,
                "",
                "</container>");

        String hostsxml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<hosts>\n" +
                "  <host name=\"mockhost-1\">\n" +
                "    <alias>mockhost1</alias>\n" +
                "  </host>\n" +
                "  <host name=\"mockhost-2\">\n" +
                "    <alias>mockhost2</alias>\n" +
                "  </host>\n" +
                "</hosts>\n";
        DeployState deployState = new DeployState.Builder()
                .properties(
                        new TestProperties()
                                .setHostedVespa(true)
                                .setEndpointCertificateSecrets(Optional.of(new EndpointCertificateSecrets("CERT", "KEY"))))
                .modelHostProvisioner(new HostsXmlProvisioner(new StringReader(hostsxml)))
                .build();
        MockRoot root = new MockRoot("root", deployState);
        createModel(root, deployState, null, clusterElem);
        ConnectorConfig sslProvider = root.getConfig(ConnectorConfig.class, "default/http/jdisc-jetty/default");
        assertFalse(sslProvider.ssl().enabled());
        assertEquals("", sslProvider.ssl().certificate());
        assertEquals("", sslProvider.ssl().privateKey());

        ConnectorConfig providedTls = root.getConfig(ConnectorConfig.class, "default/http/jdisc-jetty/tls4443");
        assertTrue(providedTls.ssl().enabled());
        assertEquals("CERT", providedTls.ssl().certificate());
        assertEquals("KEY", providedTls.ssl().privateKey());
        assertEquals(4443, providedTls.listenPort());

    }

    private static void assertChildComponentExists(ConnectorFactory connectorFactory, String className) {
        Optional<SimpleComponent> simpleComponent = connectorFactory.getChildren().values().stream()
                .map(z -> (SimpleComponent) z)
                .filter(component -> component.getClassId().stringValue().equals(className))
                .findFirst();
        assertTrue(simpleComponent.isPresent());
    }

    private void assertJettyServerInConfig() {
        ContainerCluster<?> cluster = (ContainerCluster<?>) root.getChildren().get("default");
        List<JettyHttpServer> jettyServers = cluster.getChildrenByTypeRecursive(JettyHttpServer.class);

        assertEquals(1, jettyServers.size());

        JettyHttpServer server = jettyServers.get(0);
        assertEquals(com.yahoo.jdisc.http.server.jetty.JettyHttpServer.class.getName(),
                server.model.bundleInstantiationSpec.classId.toString());
        assertEquals(com.yahoo.jdisc.http.server.jetty.JettyHttpServer.class.getName(),
                server.model.bundleInstantiationSpec.bundle.toString());
        assertEquals(1, server.getConnectorFactories().size());

        assertNotNull(extractComponentByClassName(
                containerComponentsConfig(),
                com.yahoo.jdisc.http.server.jetty.JettyHttpServer.class.getName()));
    }

    private static ComponentsConfig.Components extractComponentByClassName(
            final ComponentsConfig componentsConfig, final String className) {
        for (final ComponentsConfig.Components component : componentsConfig.components()) {
            if (className.equals(component.classId())) {
                return component;
            }
        }
        return null;
    }

    private static ComponentsConfig.Components.Inject extractInjectionById(
            final ComponentsConfig.Components component, final String id) {
        for (final ComponentsConfig.Components.Inject injection : component.inject()) {
            if (id.equals(injection.id())) {
                return injection;
            }
        }
        return null;
    }

    private ComponentsConfig containerComponentsConfig() {
        final ApplicationContainerCluster cluster = (ApplicationContainerCluster) root.getChildren().get("default");
        return root.getConfig(
                ComponentsConfig.class,
                cluster.getContainers().get(0).getConfigId());
    }

    private ComponentsConfig clusterComponentsConfig() {
        return componentsConfig();
    }
}
