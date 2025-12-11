// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
public class DataplaneProxyChangeValidatorTest {

    @TempDir
    public File applicationFolder;

    private static final String SERVICES_XML = """
            <services version='1.0'>
                <container id='default' version='1.0'>
                    <nodes count='1'/>
                </container>
            </services>
            """;

    @BeforeEach
    public void setup() throws IOException {
        var securityFolder = applicationFolder.toPath().resolve("security");
        Files.createDirectories(securityFolder);

        var keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        var subject = new X500Principal("CN=test-client");
        var certificate = X509CertificateBuilder
                .fromKeypair(keyPair, subject, Instant.now(),
                             Instant.now().plus(1, ChronoUnit.DAYS),
                             SignatureAlgorithm.SHA512_WITH_ECDSA, BigInteger.valueOf(1))
                .build();
        var certPem = X509CertificateUtils.toPem(certificate);
        Files.writeString(securityFolder.resolve("clients.pem"), certPem);
    }

    @Test
    void restart_when_token_endpoint_enabled() {
        var previous = createModel(Set.of(mtlsEndpoint("default")));
        var next = createModel(Set.of(tokenEndpoint("default"), mtlsEndpoint("default")));
        var result = validateModel(previous, next);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getMessage().contains("Token endpoint was enabled"));
        assertEquals(ConfigChangeAction.Type.RESTART, result.get(0).getType());

        assertTrue(getDeferChangesUntilRestart(next));
        assertFalse(getDeferChangesUntilRestart(previous));
    }

    @Test
    void restart_when_token_endpoint_disabled() {
        var previous = createModel(Set.of(tokenEndpoint("default"), mtlsEndpoint("default")));
        var next = createModel(Set.of(mtlsEndpoint("default")));
        var result = validateModel(previous, next);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getMessage().contains("Token endpoint was disabled"));
        assertEquals(ConfigChangeAction.Type.RESTART, result.get(0).getType());

        assertTrue(getDeferChangesUntilRestart(next));
        assertFalse(getDeferChangesUntilRestart(previous));
    }

    @Test
    void no_restart_when_token_endpoint_unchanged() {
        var previous = createModel(Set.of(tokenEndpoint("default"), mtlsEndpoint("default")));
        var next = createModel(Set.of(tokenEndpoint("default"), mtlsEndpoint("default")));
        var result = validateModel(previous, next);

        assertTrue(result.isEmpty());

        assertFalse(getDeferChangesUntilRestart(next));
        assertFalse(getDeferChangesUntilRestart(previous));
    }

    @Test
    void no_restart_when_token_endpoint_not_used() {
        var previous = createModel(Set.of(mtlsEndpoint("default")));
        var next = createModel(Set.of(mtlsEndpoint("default")));
        var result = validateModel(previous, next);

        assertTrue(result.isEmpty());

        assertFalse(getDeferChangesUntilRestart(next));
        assertFalse(getDeferChangesUntilRestart(previous));
    }

    @Test
    void no_restart_when_cluster_with_token_endpoint_is_removed() {
        var servicesXmlWithOtherCluster = """
                <services version='1.0'>
                    <container id='other-cluster' version='1.0'>
                        <nodes count='1'/>
                    </container>
                </services>
                """;

        var previous = createModel(Set.of(tokenEndpoint("default")));
        var next = createModel(servicesXmlWithOtherCluster, Set.of(tokenEndpoint("other-cluster")));
        var result = validateModel(previous, next);
        assertTrue(result.isEmpty());
    }

    private static ContainerEndpoint tokenEndpoint(String clusterId) {
        return new ContainerEndpoint(
                clusterId,
                ApplicationClusterEndpoint.Scope.zone,
                List.of("token.example.com"),
                OptionalInt.empty(),
                ApplicationClusterEndpoint.RoutingMethod.exclusive,
                ApplicationClusterEndpoint.AuthMethod.token);
    }

    private static ContainerEndpoint mtlsEndpoint(String clusterId) {
        return new ContainerEndpoint(
                clusterId,
                ApplicationClusterEndpoint.Scope.zone,
                List.of("mtls.example.com"),
                OptionalInt.empty(),
                ApplicationClusterEndpoint.RoutingMethod.exclusive,
                ApplicationClusterEndpoint.AuthMethod.mtls);
    }

    private List<ConfigChangeAction> validateModel(VespaModel current, VespaModel next) {
        return ValidationTester.validateChanges(
                new DataplaneProxyChangeValidator(),
                next,
                deployStateBuilder(Set.of()).previousModel(current).build());
    }

    private VespaModel createModel(Set<ContainerEndpoint> endpoints) {
        return createModel(SERVICES_XML, endpoints);
    }

    private VespaModel createModel(String servicesXml, Set<ContainerEndpoint> endpoints) {
        var applicationPackage = new MockApplicationPackage.Builder()
                .withRoot(applicationFolder)
                .withServices(servicesXml)
                .build();
        return new VespaModelCreatorWithMockPkg(applicationPackage)
                .create(deployStateBuilder(endpoints));
    }

    private DeployState.Builder deployStateBuilder(Set<ContainerEndpoint> endpoints) {
        return new DeployState.Builder()
                .properties(new TestProperties()
                        .setHostedVespa(true)
                        .setEndpointCertificateSecrets(Optional.of(
                                new EndpointCertificateSecrets("CERT", "KEY"))))
                .zone(new Zone(SystemName.PublicCd, Environment.dev, RegionName.defaultName()))
                .endpoints(endpoints);
    }

    private static boolean getDeferChangesUntilRestart(VespaModel model) {
        return model.getContainerClusters().get("default").getDeferChangesUntilRestart();
    }
}

