// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

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
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudDataPlaneFilterConfig;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.model.container.ApplicationContainer;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.http.ConnectorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CloudDataPlaneFilterTest extends ContainerModelBuilderTestBase {

    @TempDir
    public File applicationFolder;

    Path securityFolder;
    private static final String cloudDataPlaneFilterConfigId = "container/filters/chain/cloud-data-plane-secure/component/" +
                                                               "com.yahoo.jdisc.http.filter.security.cloud.CloudDataPlaneFilter";

    @BeforeEach
    public void setup() throws IOException {
        securityFolder = applicationFolder.toPath().resolve("security");
        Files.createDirectories(securityFolder);
    }

    @Test
    public void it_generates_correct_config() throws IOException {
        Path certFile = securityFolder.resolve("foo.pem");
        Element clusterElem = DomBuilderTest.parse(
                """ 
                        <container version='1.0'>
                          <clients>
                            <client id="foo" permissions="read,write">
                                <certificate file="%s"/>
                            </client>
                          </clients>
                        </container>
                        """
                        .formatted(applicationFolder.toPath().relativize(certFile).toString()));
        X509Certificate certificate = createCertificate(certFile);

        buildModel(clusterElem);

        CloudDataPlaneFilterConfig config = root.getConfig(CloudDataPlaneFilterConfig.class, cloudDataPlaneFilterConfigId);
        assertFalse(config.legacyMode());
        List<CloudDataPlaneFilterConfig.Clients> clients = config.clients();
        assertEquals(1, clients.size());
        CloudDataPlaneFilterConfig.Clients client = clients.get(0);
        assertEquals("foo", client.id());
        assertIterableEquals(List.of("read", "write"), client.permissions());
        assertTrue(client.tokens().isEmpty());
        assertIterableEquals(List.of(X509CertificateUtils.toPem(certificate)), client.certificates());

        ConnectorConfig connectorConfig = connectorConfig();
        var caCerts = X509CertificateUtils.certificateListFromPem(connectorConfig.ssl().caCertificate());
        assertEquals(1, caCerts.size());
        assertEquals(List.of(certificate), caCerts);
        var srvCfg = root.getConfig(ServerConfig.class, "container/http");
        assertEquals("cloud-data-plane-insecure", srvCfg.defaultFilters().get(0).filterId());
        assertEquals(8080, srvCfg.defaultFilters().get(0).localPort());
        assertEquals("cloud-data-plane-secure", srvCfg.defaultFilters().get(1).filterId());
        assertEquals(4443, srvCfg.defaultFilters().get(1).localPort());
    }

    @Test
    public void it_generates_correct_legacy_config() throws IOException {
        Path certFile = securityFolder.resolve("clients.pem");
        Element clusterElem = DomBuilderTest.parse("<container version='1.0' />");
        X509Certificate certificate = createCertificate(certFile);

        buildModel(clusterElem);

        CloudDataPlaneFilterConfig config = root.getConfig(CloudDataPlaneFilterConfig.class, cloudDataPlaneFilterConfigId);
        assertTrue(config.legacyMode());
        List<CloudDataPlaneFilterConfig.Clients> clients = config.clients();
        assertEquals(0, clients.size());

        ConnectorConfig connectorConfig = connectorConfig();
        var caCerts = X509CertificateUtils.certificateListFromPem(connectorConfig.ssl().caCertificate());
        assertEquals(1, caCerts.size());
        assertEquals(List.of(certificate), caCerts);
    }

    @Test
    void generates_correct_config_for_tokens() throws IOException {
        var certFile = securityFolder.resolve("foo.pem");
        var clusterElem = DomBuilderTest.parse(
                """ 
                        <container version='1.0'>
                          <clients>
                            <client id="foo" permissions="read,write">
                                <certificate file="%s"/>
                            </client>
                            <client id="bar" permissions="read">
                                <token id="my-token"/>
                            </client>
                          </clients>
                        </container>
                        """
                        .formatted(applicationFolder.toPath().relativize(certFile).toString()));
        createCertificate(certFile);
        buildModel(clusterElem);

        var cfg = root.getConfig(CloudDataPlaneFilterConfig.class, cloudDataPlaneFilterConfigId);
        var tokenClient = cfg.clients().stream().filter(c -> c.id().equals("bar")).findAny().orElse(null);
        assertNotNull(tokenClient);
        assertEquals(List.of("read"), tokenClient.permissions());
        assertTrue(tokenClient.certificates().isEmpty());
        var expectedTokenCfg = tokenConfig(
                "my-token", List.of("myfingerprint1", "myfingerprint2"), List.of("myaccesshash1", "myaccesshash2"));
        assertEquals(List.of(expectedTokenCfg), tokenClient.tokens());
    }

    private static CloudDataPlaneFilterConfig.Clients.Tokens tokenConfig(
            String id, Collection<String> fingerprints, Collection<String> accessCheckHashes) {
        return new CloudDataPlaneFilterConfig.Clients.Tokens.Builder()
                .id(id).fingerprints(fingerprints).checkAccessHashes(accessCheckHashes).build();
    }

    @Test
    public void it_rejects_files_without_certificates() throws IOException {
        Path certFile = securityFolder.resolve("foo.pem");
        Element clusterElem = DomBuilderTest.parse(
                """ 
                        <container version='1.0'>
                          <clients>
                            <client id="foo" permissions="read,write">
                                <certificate file="%s"/>
                            </client>
                          </clients>
                        </container>
                        """
                        .formatted(applicationFolder.toPath().relativize(certFile).toString()));
        Files.writeString(certFile, "effectively empty");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> buildModel(clusterElem));
        assertEquals("File security/foo.pem does not contain any certificates.", exception.getMessage());
    }

    @Test
    public void it_rejects_invalid_client_ids() throws IOException {
        Element clusterElem = DomBuilderTest.parse(
                """ 
                        <container version='1.0'>
                          <clients>
                            <client id="_foo" permissions="read,write">
                                <certificate file="foo"/>
                            </client>
                          </clients>
                        </container>
                        """);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> buildModel(clusterElem));
        assertEquals("Invalid client id '_foo', id cannot start with '_'", exception.getMessage());
    }

    private ConnectorConfig connectorConfig() {
        ApplicationContainer container = (ApplicationContainer) root.getProducer("container/container.0");
        List<ConnectorFactory> connectorFactories = container.getHttp().getHttpServer().get().getConnectorFactories();
        ConnectorFactory tlsPort = connectorFactories.stream().filter(connectorFactory -> connectorFactory.getListenPort() == 4443).findFirst().orElseThrow();

        ConnectorConfig.Builder builder = new ConnectorConfig.Builder();
        tlsPort.getConfig(builder);

        return new ConnectorConfig(builder);
    }

    /*
    Creates cert, returns
     */
    static X509Certificate createCertificate(Path certFile) throws IOException {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        X500Principal subject = new X500Principal("CN=mysubject");
        X509Certificate certificate = X509CertificateBuilder
                .fromKeypair(
                        keyPair, subject, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), SignatureAlgorithm.SHA512_WITH_ECDSA, BigInteger.valueOf(1))
                .build();
        String certPem = X509CertificateUtils.toPem(certificate);
        Files.writeString(certFile, certPem);
        return certificate;
    }

    public List<ContainerModel> buildModel(Element... clusterElem) {
        var applicationPackage = new MockApplicationPackage.Builder()
                .withRoot(applicationFolder)
                .build();

        DeployState state = new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .properties(
                        new TestProperties()
                                .setEndpointCertificateSecrets(Optional.of(new EndpointCertificateSecrets("CERT", "KEY")))
                                .setDataplaneTokens(List.of(new DataplaneToken("my-token", List.of(
                                        new DataplaneToken.Version("myfingerprint1", "myaccesshash1"),
                                        new DataplaneToken.Version("myfingerprint2", "myaccesshash2")))))
                                .setHostedVespa(true))
                .zone(new Zone(SystemName.PublicCd, Environment.dev, RegionName.defaultName()))
                .build();
        return createModel(root, state, null, clusterElem);
    }
}
