// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.cloud.config.DataplaneProxyConfig;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.DataplaneToken;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudTokenDataPlaneFilterConfig;
import com.yahoo.text.Text;
import com.yahoo.vespa.model.container.ApplicationContainer;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.http.ConnectorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static com.yahoo.vespa.model.container.xml.CloudDataPlaneFilterTest.createCertificate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CloudTokenDataPlaneFilterTest extends ContainerModelBuilderTestBase {

    private static final String servicesXmlTemplate = """
            <container version='1.0'>
              <clients>
                <client id="foo" permissions="read, write">
                    <certificate file="%s"/>
                </client>
                <client id="bar" permissions="read">
                    <token id="my-token"/>
                </client>
              </clients>
            </container>
            """;

    private static final List<DataplaneToken> defaultTokens = List.of(new DataplaneToken("my-token", List.of(
            new DataplaneToken.Version("myfingerprint1", "myaccesshash1", Optional.empty()),
            new DataplaneToken.Version("myfingerprint2", "myaccesshash2", Optional.of(Instant.EPOCH.plus(Duration.ofDays(100000)))))));
    private static final ContainerEndpoint tokenEndpoint = new ContainerEndpoint("container", ApplicationClusterEndpoint.Scope.zone, List.of("token"), OptionalInt.empty(), ApplicationClusterEndpoint.RoutingMethod.exclusive, ApplicationClusterEndpoint.AuthMethod.token);
    private static final ContainerEndpoint mtlsEndpoint = new ContainerEndpoint("container", ApplicationClusterEndpoint.Scope.zone, List.of("mtls"), OptionalInt.empty(), ApplicationClusterEndpoint.RoutingMethod.exclusive, ApplicationClusterEndpoint.AuthMethod.mtls);

    @TempDir
    public File applicationFolder;

    Path securityFolder;
    private static final String filterConfigId = "container/filters/chain/cloud-token-data-plane-secure/component/" +
            "com.yahoo.jdisc.http.filter.security.cloud.CloudTokenDataPlaneFilter";

    @BeforeEach
    public void setup() throws IOException {
        securityFolder = applicationFolder.toPath().resolve("security");
        Files.createDirectories(securityFolder);
    }

    @Test
    void generates_correct_config_for_tokens() throws IOException {
        var certFile = securityFolder.resolve("foo.pem");
        var clusterElem = DomBuilderTest.parse(Text.format(servicesXmlTemplate, applicationFolder.toPath().relativize(certFile).toString()));
        createCertificate(certFile);
        buildModel(Set.of(tokenEndpoint, mtlsEndpoint), defaultTokens, clusterElem);

        var cfg = root.getConfig(CloudTokenDataPlaneFilterConfig.class, filterConfigId);
        var tokenClient = cfg.clients().stream().filter(c -> c.id().equals("bar")).findAny().orElse(null);
        assertNotNull(tokenClient);
        assertEquals(List.of("read"), tokenClient.permissions());
        var expectedTokenCfg = tokenConfig(
                "my-token", List.of("myfingerprint1", "myfingerprint2"), List.of("myaccesshash1", "myaccesshash2"),
                List.of("<none>", "2243-10-17T00:00:00Z"));
        assertEquals(List.of(expectedTokenCfg), tokenClient.tokens());
    }

    @Test
    void configures_dataplane_proxy_when_token_defined() throws IOException {
        var certFile = securityFolder.resolve("foo.pem");
        var clusterElem = DomBuilderTest.parse(Text.format(servicesXmlTemplate, applicationFolder.toPath().relativize(certFile).toString()));
        createCertificate(certFile);
        buildModel(Set.of(tokenEndpoint, mtlsEndpoint), defaultTokens, clusterElem);

        var configId = "container/component/com.yahoo.vespa.cloud.tenant.dataplane.DataplaneProxyConfigurator";
        var cfg = root.getConfig(DataplaneProxyConfig.class, configId);
        assertEquals(8443, cfg.mtlsPort());
        assertEquals(8444, cfg.tokenPort());
    }

    @Test
    void configures_dataplane_proxy_when_token_defined_but_missing() throws IOException {
        var certFile = securityFolder.resolve("foo.pem");
        var clusterElem = DomBuilderTest.parse(Text.format(servicesXmlTemplate, applicationFolder.toPath().relativize(certFile).toString()));
        createCertificate(certFile);
        buildModel(Set.of(tokenEndpoint, mtlsEndpoint), List.of(), clusterElem);

        var configId = "container/component/com.yahoo.vespa.cloud.tenant.dataplane.DataplaneProxyConfigurator";
        var cfg = root.getConfig(DataplaneProxyConfig.class, configId);
        assertNotNull(cfg);
        assertEquals(8443, cfg.mtlsPort());
        assertEquals(8444, cfg.tokenPort());
    }

    @Test
    void does_notconfigure_dataplane_proxy_when_token_endpoints_not_defined() throws IOException {
        var certFile = securityFolder.resolve("foo.pem");
        var clusterElem = DomBuilderTest.parse(Text.format(servicesXmlTemplate, applicationFolder.toPath().relativize(certFile).toString()));
        createCertificate(certFile);
        buildModel(Set.of(mtlsEndpoint), List.of(), clusterElem);

        assertFalse(root.getConfigIds().stream().anyMatch(id -> id.contains("DataplaneProxyConfigurator")));
    }

    @Test
    void configuresCorrectConnectors() throws IOException {
        var certFile = securityFolder.resolve("foo.pem");
        var clusterElem = DomBuilderTest.parse(Text.format(servicesXmlTemplate, applicationFolder.toPath().relativize(certFile).toString()));
        createCertificate(certFile);
        buildModel(Set.of(tokenEndpoint, mtlsEndpoint), defaultTokens, clusterElem);

        ConnectorConfig connectorConfig8443 = connectorConfig(8443);
        assertEquals(List.of("mtls"),connectorConfig8443.serverName().known());

        ConnectorConfig connectorConfig8444 = connectorConfig(8444);
        assertEquals(List.of("token"),connectorConfig8444.serverName().known());

    }

    @Test
    void fails_on_unknown_permission() throws IOException {
        var certFile = securityFolder.resolve("foo.pem");
        var servicesXml = Text.format("""
                <container version='1.0'>
                  <clients>
                    <client id="foo" permissions="read,unknown-permission">
                        <certificate file="%s"/>
                    </client>
                  </clients>
                </container>
                """, applicationFolder.toPath().relativize(certFile).toString());
        var clusterElem = DomBuilderTest.parse(servicesXml);
        createCertificate(certFile);
        var exception = assertThrows(IllegalArgumentException.class, () -> buildModel(Set.of(mtlsEndpoint), defaultTokens, clusterElem));
        assertEquals("Invalid permission 'unknown-permission'. Valid values are 'read' and 'write'.", exception.getMessage());
    }

    @Test
    void fails_on_duplicate_clients() throws IOException {
        var certFile = securityFolder.resolve("foo.pem");
        var servicesXml = Text.format("""
                    <container version="1.0">
                        <clients>
                            <client id="mtls" permissions="read,write">
                              <certificate file="%1$s"/>
                            </client>
                            <client id="mtls" permissions="read,write">
                              <certificate file="%1$s"/>
                            </client>
                            <client id="token1" permissions="read">
                                <token id="my-token"/>
                            </client>
                            <client id="token2" permissions="read">
                                <token id="my-token"/>
                            </client>
                            <client id="token1" permissions="read">
                                <token id="my-token"/>
                            </client>
                        </clients>
                    </container>
                """, applicationFolder.toPath().relativize(certFile).toString());
        var clusterElem = DomBuilderTest.parse(servicesXml);
        createCertificate(certFile);
        var exception = assertThrows(IllegalArgumentException.class, () -> buildModel(Set.of(mtlsEndpoint), defaultTokens, clusterElem));
        assertEquals("Duplicate client ids: [mtls, token1]", exception.getMessage());
    }

    private static CloudTokenDataPlaneFilterConfig.Clients.Tokens tokenConfig(
            String id, Collection<String> fingerprints, Collection<String> accessCheckHashes, Collection<String> expirations) {
        return new CloudTokenDataPlaneFilterConfig.Clients.Tokens.Builder()
                .id(id).fingerprints(fingerprints).checkAccessHashes(accessCheckHashes).expirations(expirations).build();
    }

    public List<ContainerModel> buildModel(Set<ContainerEndpoint> endpoints, List<DataplaneToken> definedTokens, Element... clusterElem) {
        var applicationPackage = new MockApplicationPackage.Builder()
                .withRoot(applicationFolder)
                .build();

        DeployState state = new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .properties(
                        new TestProperties()
                        .setEndpointCertificateSecrets(Optional.of(new EndpointCertificateSecrets("CERT", "KEY")))
                        .setDataplaneTokens(definedTokens)
                        .setHostedVespa(true))
                .zone(new Zone(SystemName.PublicCd, Environment.dev, RegionName.defaultName()))
                .endpoints(endpoints)
                .build();
        return createModel(root, state, null, clusterElem);
    }

    private ConnectorConfig connectorConfig(int port) {
        ApplicationContainer container = (ApplicationContainer) root.getProducer("container/container.0");
        List<ConnectorFactory> connectorFactories = container.getHttp().getHttpServer().get().getConnectorFactories();
        ConnectorFactory tlsPort = connectorFactories.stream().filter(connectorFactory -> connectorFactory.getListenPort() == port).findFirst().orElseThrow();

        ConnectorConfig.Builder builder = new ConnectorConfig.Builder();
        tlsPort.getConfig(builder);

        return new ConnectorConfig(builder);
    }
}
