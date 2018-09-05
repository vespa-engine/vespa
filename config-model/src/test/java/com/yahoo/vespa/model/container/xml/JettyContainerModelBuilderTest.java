// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.jdisc.FilterBindingsProvider;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ssl.DefaultSslKeyStoreConfigurator;
import com.yahoo.jdisc.http.ssl.DefaultSslTrustStoreConfigurator;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import com.yahoo.vespa.model.container.http.ConnectorFactory;
import com.yahoo.vespa.model.container.http.JettyHttpServer;
import org.junit.Test;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * @author einarmr
 */
public class JettyContainerModelBuilderTest extends ContainerModelBuilderTestBase {

    @Test
    public void verify_that_overriding_connector_options_works() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>\n" +
                "  <http>\n" +
                "    <server id='bananarama' port='4321'>\n" +
                "      <config name='jdisc.http.connector'>\n" +
                "        <requestHeaderSize>300000</requestHeaderSize>\n" +
                "        <headerCacheSize>300000</headerCacheSize>\n" +
                "      </config>\n" +
                "    </server>\n" +
                "  </http>\n" +
                nodesXml +
                "</jdisc>\n"
        );
        createModel(root, clusterElem);
        ConnectorConfig cfg = root.getConfig(ConnectorConfig.class, "default/http/jdisc-jetty/bananarama");
        assertThat(cfg.requestHeaderSize(), is(300000));
        assertThat(cfg.headerCacheSize(), is(300000));
    }

    @Test
    public void verify_that_enabling_jetty_works() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>" +
                        nodesXml +
                        "</jdisc>"
        );
        createModel(root, clusterElem);
        assertJettyServerInConfig();
    }

    @Test
    public void verify_that_enabling_jetty_works_for_custom_http_servers() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>",
                "  <http>",
                "    <server port='9000' id='foo' />",
                "  </http>",
                nodesXml,
                "</jdisc>" );
        createModel(root, clusterElem);
        assertJettyServerInConfig();
    }

    @Test
    public void verifyThatJettyHttpServerHasFilterBindingsProvider() throws Exception {
        final Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>",
                nodesXml,
                "</jdisc>" );
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
    public void verifyThatJettyHttpServerHasFilterBindingsProviderForCustomHttpServers() throws Exception {
        final Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>",
                "  <http>",
                "    <server port='9000' id='foo' />",
                "  </http>",
                nodesXml,
                "</jdisc>" );
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
    public void ssl_keystore_and_truststore_configurator_can_be_overriden() throws IOException, SAXException {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>",
                "  <http>",
                "    <server port='9000' id='foo'>",
                "      <ssl-keystore-configurator class='com.yahoo.MySslKeyStoreConfigurator' bundle='mybundle'/>",
                "      <ssl-truststore-configurator class='com.yahoo.MySslTrustStoreConfigurator' bundle='mybundle'/>",
                "    </server>",
                "    <server port='9001' id='bar'/>",
                "  </http>",
                nodesXml,
                "</jdisc>");
        createModel(root, clusterElem);
        ContainerCluster cluster = (ContainerCluster) root.getChildren().get("default");
        List<ConnectorFactory> connectorFactories = cluster.getChildrenByTypeRecursive(ConnectorFactory.class);
        {
            ConnectorFactory firstConnector = connectorFactories.get(0);
            assertConnectorHasInjectedComponents(firstConnector, "ssl-keystore-configurator@foo", "ssl-truststore-configurator@foo");
            assertComponentHasClassNameAndBundle(getChildComponent(firstConnector, 0),
                                                 "com.yahoo.MySslKeyStoreConfigurator",
                                                 "mybundle");
            assertComponentHasClassNameAndBundle(getChildComponent(firstConnector, 1),
                                                 "com.yahoo.MySslTrustStoreConfigurator",
                                                 "mybundle");
        }
        {
            ConnectorFactory secondConnector = connectorFactories.get(1);
            assertConnectorHasInjectedComponents(secondConnector, "ssl-keystore-configurator@bar", "ssl-truststore-configurator@bar");
            assertComponentHasClassNameAndBundle(getChildComponent(secondConnector, 0),
                                                 DefaultSslKeyStoreConfigurator.class.getName(),
                                                 "jdisc_http_service");
            assertComponentHasClassNameAndBundle(getChildComponent(secondConnector, 1),
                                                 DefaultSslTrustStoreConfigurator.class.getName(),
                                                 "jdisc_http_service");
        }
    }

    private static void assertConnectorHasInjectedComponents(ConnectorFactory connectorFactory, String... componentNames) {
        Set<String> injectedComponentIds = connectorFactory.getInjectedComponentIds();
        assertThat(injectedComponentIds.size(), equalTo(componentNames.length));
        Arrays.stream(componentNames)
                .forEach(name -> assertThat(injectedComponentIds, hasItem(name)));
    }

    private static SimpleComponent getChildComponent(ConnectorFactory connectorFactory, int index) {
        return connectorFactory.getChildrenByTypeRecursive(SimpleComponent.class).get(index);
    }

    private static void assertComponentHasClassNameAndBundle(SimpleComponent simpleComponent,
                                                             String className,
                                                             String bundleName) {
        BundleInstantiationSpecification spec = simpleComponent.model.bundleInstantiationSpec;
        assertThat(spec.classId.toString(), is(className));
        assertThat(spec.bundle.toString(), is(bundleName));
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
        final ContainerCluster cluster = (ContainerCluster) root.getChildren().get("default");
        return root.getConfig(
                ComponentsConfig.class,
                cluster.getContainers().get(0).getConfigId());
    }

    private ComponentsConfig clusterComponentsConfig() {
        return componentsConfig();
    }
}
