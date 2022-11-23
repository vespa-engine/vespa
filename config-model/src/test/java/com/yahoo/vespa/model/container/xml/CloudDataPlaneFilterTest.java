// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
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
import com.yahoo.vespa.model.container.http.ConnectorFactory;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CloudDataPlaneFilterTest extends ContainerModelBuilderTestBase {

    @TempDir
    public File applicationFolder;

    @Test
    public void it_generates_correct_config() throws IOException {
        Path security = applicationFolder.toPath().resolve("security");
        Files.createDirectories(security);
        Path certFile = security.resolve("foo.pem");
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
        X509Certificate certificate = createCertificate();
        String certPem = X509CertificateUtils.toPem(certificate);
        Files.writeString(certFile, certPem);

        var applicationPackage = new MockApplicationPackage.Builder()
                .withRoot(applicationFolder)
                .build();

        DeployState state = new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .properties(
                        new TestProperties()
                                .setEndpointCertificateSecrets(Optional.of(new EndpointCertificateSecrets("CERT", "KEY")))
                                .setHostedVespa(true)
                                .setEnableDataPlaneFilter(true))
                .zone(new Zone(SystemName.PublicCd, Environment.dev, RegionName.defaultName()))
                .build();
        createModel(root, state, null, clusterElem);

        String configId = "container/filters/chain/cloud-data-plane-secure/component/" +
                "com.yahoo.jdisc.http.filter.security.cloud.CloudDataPlaneFilter";
        CloudDataPlaneFilterConfig config = root.getConfig(CloudDataPlaneFilterConfig.class, configId);
        assertFalse(config.legacyMode());
        List<CloudDataPlaneFilterConfig.Clients> clients = config.clients();
        assertEquals(1, clients.size());
        CloudDataPlaneFilterConfig.Clients client = clients.get(0);
        assertEquals("foo", client.id());
        assertIterableEquals(List.of("read", "write"), client.permissions());
        assertIterableEquals(List.of(certPem), client.certificates());

        ApplicationContainer container = (ApplicationContainer) root.getProducer("container/container.0");
        List<ConnectorFactory> connectorFactories = container.getHttp().getHttpServer().get().getConnectorFactories();
        ConnectorFactory tlsPort = connectorFactories.stream().filter(connectorFactory -> connectorFactory.getListenPort() == 4443).findFirst().orElseThrow();

        ConnectorConfig.Builder builder = new ConnectorConfig.Builder();
        tlsPort.getConfig(builder);

        ConnectorConfig connectorConfig = new ConnectorConfig(builder);
        var caCerts = X509CertificateUtils.certificateListFromPem(connectorConfig.ssl().caCertificate());
        assertEquals(1, caCerts.size());
        assertEquals(List.of(certificate), caCerts);
        var srvCfg = root.getConfig(ServerConfig.class, "container/http");
        assertEquals("cloud-data-plane-insecure", srvCfg.defaultFilters().get(0).filterId());
        assertEquals(8080, srvCfg.defaultFilters().get(0).localPort());
        assertEquals("cloud-data-plane-secure", srvCfg.defaultFilters().get(1).filterId());
        assertEquals(4443, srvCfg.defaultFilters().get(1).localPort());
    }



    static X509Certificate createCertificate()  {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        X500Principal subject = new X500Principal("CN=mysubject");
        return X509CertificateBuilder
                .fromKeypair(
                        keyPair, subject, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), SignatureAlgorithm.SHA512_WITH_ECDSA, BigInteger.valueOf(1))
                .build();
    }

}
