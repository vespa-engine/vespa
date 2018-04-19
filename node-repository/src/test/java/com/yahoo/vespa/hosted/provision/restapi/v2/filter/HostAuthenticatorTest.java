package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.vespa.athenz.tls.KeyUtils;
import com.yahoo.vespa.athenz.tls.Pkcs10Csr;
import com.yahoo.vespa.athenz.tls.Pkcs10CsrBuilder;
import com.yahoo.vespa.athenz.tls.X509CertificateBuilder;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepositoryTester;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Optional;

import static com.yahoo.vespa.athenz.tls.KeyAlgorithm.RSA;
import static com.yahoo.vespa.athenz.tls.SignatureAlgorithm.SHA256_WITH_RSA;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

/**
 * @author bjorncs
 */
public class HostAuthenticatorTest {
    private static final String HOSTNAME = "myhostname";
    private static final String OPENSTACK_ID = "OPENSTACK-ID";
    private static final String INSTANCE_ID = "default";
    private static final Zone ZONE = new Zone(SystemName.main, Environment.prod, RegionName.defaultName());
    private static final KeyPair KEYPAIR = KeyUtils.generateKeypair(RSA);
    private static final X509Certificate ATHENZ_CA_DUMMY = createAthenzCaDummyCertificate();

    @Test
    public void accepts_configserver_selfsigned_cert() {
        NodeRepositoryTester nodeRepositoryDummy = new NodeRepositoryTester();
        X509Certificate certificate = X509CertificateBuilder
                .fromKeypair(
                        KEYPAIR, new X500Principal("CN=" + HOSTNAME), Instant.EPOCH, Instant.EPOCH.plusSeconds(60), SHA256_WITH_RSA, 1)
                .build();
        HostAuthenticator authenticator = new HostAuthenticator(ZONE, nodeRepositoryDummy.nodeRepository());
        TlsPrincipal identity = authenticator.authenticate(singletonList(certificate));
        assertEquals(HOSTNAME, identity.getName());
    }

    @Test
    public void accepts_openstack_host_certificate() {
        NodeRepositoryTester nodeRepositoryDummy = new NodeRepositoryTester();
        nodeRepositoryDummy.addNode(OPENSTACK_ID, HOSTNAME, INSTANCE_ID, NodeType.host);
        nodeRepositoryDummy.setNodeState(HOSTNAME, Node.State.active);
        Pkcs10Csr csr = Pkcs10CsrBuilder
                .fromKeypair(new X500Principal("CN=vespa.vespa.tenant-host"), KEYPAIR, SHA256_WITH_RSA)
                .build();
        X509Certificate certificate = X509CertificateBuilder
                .fromCsr(csr, ATHENZ_CA_DUMMY.getSubjectX500Principal(), Instant.EPOCH, Instant.EPOCH.plusSeconds(60), KEYPAIR.getPrivate(), SHA256_WITH_RSA, 1)
                .addSubjectAlternativeName(OPENSTACK_ID + ".instanceid.athenz.provider-name.ostk.yahoo.cloud")
                .build();
        HostAuthenticator authenticator = new HostAuthenticator(ZONE, nodeRepositoryDummy.nodeRepository());
        TlsPrincipal identity = authenticator.authenticate(singletonList(certificate));
        assertEquals(HOSTNAME, identity.getName());
    }

    @Test
    public void accepts_docker_container_certificate() {
        String clusterId = "clusterid";
        int clusterIndex = 0;
        String tenant = "tenant";
        String application = "application";
        String region = ZONE.region().value();
        String environment = ZONE.environment().value();
        NodeRepositoryTester nodeRepositoryDummy = new NodeRepositoryTester();
        Node node = createNode(clusterId, clusterIndex, tenant, application);
        nodeRepositoryDummy.nodeRepository().addDockerNodes(singletonList(node));
        Pkcs10Csr csr = Pkcs10CsrBuilder
                .fromKeypair(new X500Principal("CN=vespa.vespa.tenant"), KEYPAIR, SHA256_WITH_RSA)
                .build();
        VespaUniqueInstanceId vespaUniqueInstanceId = new VespaUniqueInstanceId(clusterIndex, clusterId, INSTANCE_ID, application, tenant, region, environment);
        X509Certificate certificate = X509CertificateBuilder
                .fromCsr(csr, ATHENZ_CA_DUMMY.getSubjectX500Principal(), Instant.EPOCH, Instant.EPOCH.plusSeconds(60), KEYPAIR.getPrivate(), SHA256_WITH_RSA, 1)
                .addSubjectAlternativeName(vespaUniqueInstanceId.asDottedString() + ".instanceid.athenz.provider-name.vespa.yahoo.cloud")
                .build();
        HostAuthenticator authenticator = new HostAuthenticator(ZONE, nodeRepositoryDummy.nodeRepository());
        TlsPrincipal identity = authenticator.authenticate(singletonList(certificate));
        assertEquals(HOSTNAME, identity.getName());
    }

    private static Node createNode(String clusterId, int clusterIndex, String tenant, String application) {
        return Node
                .createDockerNode(
                        OPENSTACK_ID,
                        singleton("1.2.3.4"),
                        emptySet(),
                        HOSTNAME,
                        Optional.of("parenthost"),
                        new Flavor(createFlavourConfig().flavor(0)),
                        NodeType.tenant)
                .with(
                        new Allocation(
                                ApplicationId.from(tenant, application, INSTANCE_ID),
                                ClusterMembership.from(
                                        ClusterSpec.from(
                                                ClusterSpec.Type.container,
                                                new ClusterSpec.Id(clusterId),
                                                ClusterSpec.Group.from(0),
                                                Version.emptyVersion),
                                        clusterIndex),
                                Generation.inital(),
                                false));

    }

    private static X509Certificate createAthenzCaDummyCertificate() {
        KeyPair keyPair = KeyUtils.generateKeypair(RSA);
        return X509CertificateBuilder
                .fromKeypair(
                        keyPair, new X500Principal("CN=Yahoo Athenz CA"), Instant.EPOCH, Instant.EPOCH.plusSeconds(60), SHA256_WITH_RSA, 1)
                .setBasicConstraints(true, true)
                .build();

    }

    private static FlavorsConfig createFlavourConfig() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("docker", 1., 2., 50, Flavor.Type.DOCKER_CONTAINER).cost(1);
        return b.build();
    }
}