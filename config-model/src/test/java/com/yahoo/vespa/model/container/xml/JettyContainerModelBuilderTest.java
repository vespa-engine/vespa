// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.api.TlsSecrets;
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
import org.junit.Test;
import org.w3c.dom.Element;

import java.io.StringReader;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author einarmr
 * @author mortent
 */
public class JettyContainerModelBuilderTest extends ContainerModelBuilderTestBase {

    @Test
    public void verify_that_overriding_connector_options_works() {
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
        assertThat(cfg.requestHeaderSize(), is(300000));
        assertThat(cfg.headerCacheSize(), is(300000));
    }

    @Test
    public void verify_that_enabling_jetty_works() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>" +
                        nodesXml +
                        "</container>"
        );
        createModel(root, clusterElem);
        assertJettyServerInConfig();
    }

    @Test
    public void verify_that_enabling_jetty_works_for_custom_http_servers() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <http>",
                "    <server port='9000' id='foo' />",
                "  </http>",
                nodesXml,
                "</container>" );
        createModel(root, clusterElem);
        assertJettyServerInConfig();
    }

    @Test
    public void verifyThatJettyHttpServerHasFilterBindingsProvider() {
        final Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                nodesXml,
                "</container>" );
        createModel(root, clusterElem);

        final ComponentsConfig.Components jettyHttpServerComponent = extractComponentByClassName(
                containerComponentsConfig(), com.yahoo.jdisc.http.server.jetty.JettyHttpServer.class.getName());
        assertThat(jettyHttpServerComponent, is(not(nullValue())));

        final ComponentsConfig.Components filterBindingsProviderComponent = extractComponentByClassName(
                containerComponentsConfig(), FilterBindingsProvider.class.getName());
        assertThat(filterBindingsProviderComponent, is(not(nullValue())));

        final ComponentsConfig.Components.Inject filterBindingsProviderInjection = extractInjectionById(
                jettyHttpServerComponent, filterBindingsProviderComponent.id());
        assertThat(filterBindingsProviderInjection, is(not(nullValue())));
    }

    @Test
    public void verifyThatJettyHttpServerHasFilterBindingsProviderForCustomHttpServers() {
        final Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <http>",
                "    <server port='9000' id='foo' />",
                "  </http>",
                nodesXml,
                "</container>" );
        createModel(root, clusterElem);

        final ComponentsConfig.Components jettyHttpServerComponent = extractComponentByClassName(
                clusterComponentsConfig(), com.yahoo.jdisc.http.server.jetty.JettyHttpServer.class.getName());
        assertThat(jettyHttpServerComponent, is(not(nullValue())));

        final ComponentsConfig.Components filterBindingsProviderComponent = extractComponentByClassName(
                clusterComponentsConfig(), FilterBindingsProvider.class.getName());
        assertThat(filterBindingsProviderComponent, is(not(nullValue())));

        final ComponentsConfig.Components.Inject filterBindingsProviderInjection = extractInjectionById(
                jettyHttpServerComponent, filterBindingsProviderComponent.id());
        assertThat(filterBindingsProviderInjection, is(not(nullValue())));
    }

    @Test
    public void ssl_element_generates_connector_config_and_injects_provider_component() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0' jetty='true'>",
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
                "    </http>",
                nodesXml,
                "",
                "</container>");

        createModel(root, clusterElem);
        ConnectorConfig minimalCfg = root.getConfig(ConnectorConfig.class, "default/http/jdisc-jetty/minimal/configured-ssl-provider@minimal");
        assertTrue(minimalCfg.ssl().enabled());
        assertThat(minimalCfg.ssl().privateKeyFile(), is(equalTo("/foo/key")));
        assertThat(minimalCfg.ssl().certificateFile(), is(equalTo("/foo/cert")));
        assertThat(minimalCfg.ssl().caCertificateFile(), is(equalTo("")));
        assertThat(minimalCfg.ssl().clientAuth(), is(equalTo(ConnectorConfig.Ssl.ClientAuth.Enum.DISABLED)));

        ConnectorConfig withCaCerts = root.getConfig(ConnectorConfig.class, "default/http/jdisc-jetty/with-cacerts/configured-ssl-provider@with-cacerts");
        assertTrue(withCaCerts.ssl().enabled());
        assertThat(withCaCerts.ssl().privateKeyFile(), is(equalTo("/foo/key")));
        assertThat(withCaCerts.ssl().certificateFile(), is(equalTo("/foo/cert")));
        assertThat(withCaCerts.ssl().caCertificateFile(), is(equalTo("/foo/cacerts")));
        assertThat(withCaCerts.ssl().clientAuth(), is(equalTo(ConnectorConfig.Ssl.ClientAuth.Enum.DISABLED)));

        ConnectorConfig needClientAuth = root.getConfig(ConnectorConfig.class, "default/http/jdisc-jetty/need-client-auth/configured-ssl-provider@need-client-auth");
        assertTrue(needClientAuth.ssl().enabled());
        assertThat(needClientAuth.ssl().privateKeyFile(), is(equalTo("/foo/key")));
        assertThat(needClientAuth.ssl().certificateFile(), is(equalTo("/foo/cert")));
        assertThat(needClientAuth.ssl().caCertificateFile(), is(equalTo("")));
        assertThat(needClientAuth.ssl().clientAuth(), is(equalTo(ConnectorConfig.Ssl.ClientAuth.Enum.NEED_AUTH)));

        ContainerCluster cluster = (ContainerCluster) root.getChildren().get("default");
        List<ConnectorFactory> connectorFactories = cluster.getChildrenByTypeRecursive(ConnectorFactory.class);
        connectorFactories.forEach(connectorFactory -> assertChildComponentExists(connectorFactory, ConfiguredFilebasedSslProvider.COMPONENT_CLASS));
    }

    @Test
    public void verify_tht_ssl_provider_configuration_configures_correct_config() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0' jetty='true'>",
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

        ContainerCluster cluster = (ContainerCluster) root.getChildren().get("default");
        List<ConnectorFactory> connectorFactories = cluster.getChildrenByTypeRecursive(ConnectorFactory.class);
        ConnectorFactory connectorFactory = connectorFactories.get(0);
        assertChildComponentExists(connectorFactory, "com.yahoo.CustomSslProvider");
    }

    @Test
    public void verify_that_container_factory_sees_same_config(){
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0' jetty='true'>",
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
    public void verify_that_container_setup_additional_tls4443(){
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0' jetty='true'>",
                "    <http>",
                "        <server port='9000' id='ssl'>",
                "            <ssl>",
                "                <private-key-file>/foo/key</private-key-file>",
                "                <certificate-file>/foo/cert</certificate-file>",
                "            </ssl>",
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
                                .setTlsSecrets(Optional.of(new TlsSecrets("CERT", "KEY"))))
                .modelHostProvisioner(new HostsXmlProvisioner(new StringReader(hostsxml)))
                .build();
        MockRoot root = new MockRoot("root", deployState);
        createModel(root, deployState, null, clusterElem);
        ConnectorConfig sslProvider = root.getConfig(ConnectorConfig.class, "default/http/jdisc-jetty/ssl");
        assertTrue(sslProvider.ssl().enabled());
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
        ContainerCluster cluster = (ContainerCluster) root.getChildren().get("default");
        List<JettyHttpServer> jettyServers = cluster.getChildrenByTypeRecursive(JettyHttpServer.class);

        assertThat(jettyServers.size(), is(1));

        JettyHttpServer server = jettyServers.get(0);
        assertThat(server.model.bundleInstantiationSpec.classId.toString(),
                is(com.yahoo.jdisc.http.server.jetty.JettyHttpServer.class.getName()));
        assertThat(server.model.bundleInstantiationSpec.bundle.toString(), is("jdisc_http_service"));
        assertThat(server.getConnectorFactories().size(), is(1));

        assertThat(
                extractComponentByClassName(
                        containerComponentsConfig(),
                        com.yahoo.jdisc.http.server.jetty.JettyHttpServer.class.getName()),
                is(not(nullValue())));
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
