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
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudTokenDataPlaneFilterConfig;
import com.yahoo.vespa.model.container.ContainerModel;
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

import static com.yahoo.vespa.model.container.xml.CloudDataPlaneFilterTest.createCertificate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CloudTokenDataPlaneFilterTest extends ContainerModelBuilderTestBase {

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

        var cfg = root.getConfig(CloudTokenDataPlaneFilterConfig.class, filterConfigId);
        var tokenClient = cfg.clients().stream().filter(c -> c.id().equals("bar")).findAny().orElse(null);
        assertNotNull(tokenClient);
        assertEquals(List.of("read"), tokenClient.permissions());
        var expectedTokenCfg = tokenConfig(
                "my-token", List.of("myfingerprint1", "myfingerprint2"), List.of("myaccesshash1", "myaccesshash2"),
                List.of("<none>", "2243-10-17T00:00:00Z"));
        assertEquals(List.of(expectedTokenCfg), tokenClient.tokens());
    }

    private static CloudTokenDataPlaneFilterConfig.Clients.Tokens tokenConfig(
            String id, Collection<String> fingerprints, Collection<String> accessCheckHashes, Collection<String> expirations) {
        return new CloudTokenDataPlaneFilterConfig.Clients.Tokens.Builder()
                .id(id).fingerprints(fingerprints).checkAccessHashes(accessCheckHashes).expirations(expirations).build();
    }

    public List<ContainerModel> buildModel(Element... clusterElem) {
        var applicationPackage = new MockApplicationPackage.Builder()
                .withRoot(applicationFolder)
                .build();

        DeployState state = new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .properties(
                        new TestProperties()
                                .setEnableDataplaneProxy(true)
                                .setEndpointCertificateSecrets(Optional.of(new EndpointCertificateSecrets("CERT", "KEY")))
                                .setDataplaneTokens(List.of(new DataplaneToken("my-token", List.of(
                                        new DataplaneToken.Version("myfingerprint1", "myaccesshash1", Optional.empty()),
                                        new DataplaneToken.Version("myfingerprint2", "myaccesshash2", Optional.of(Instant.EPOCH.plus(Duration.ofDays(100000))))))))
                                .setHostedVespa(true))
                .zone(new Zone(SystemName.PublicCd, Environment.dev, RegionName.defaultName()))
                .build();
        return createModel(root, state, null, clusterElem);
    }
}
