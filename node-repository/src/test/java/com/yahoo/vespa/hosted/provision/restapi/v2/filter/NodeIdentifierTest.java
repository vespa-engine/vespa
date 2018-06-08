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

import static com.yahoo.vespa.athenz.identityprovider.api.IdentityType.*;
import static com.yahoo.vespa.athenz.tls.KeyAlgorithm.RSA;
import static com.yahoo.vespa.athenz.tls.SignatureAlgorithm.SHA256_WITH_RSA;
import static com.yahoo.vespa.hosted.provision.restapi.v2.filter.NodeIdentifier.CONFIGSERVER_HOST_IDENTITY;
import static com.yahoo.vespa.hosted.provision.restapi.v2.filter.NodeIdentifier.PROXY_HOST_IDENTITY;
import static com.yahoo.vespa.hosted.provision.restapi.v2.filter.NodeIdentifier.TENANT_DOCKER_CONTAINER_IDENTITY;
import static com.yahoo.vespa.hosted.provision.restapi.v2.filter.NodeIdentifier.TENANT_DOCKER_HOST_IDENTITY;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bjorncs
 */
public class NodeIdentifierTest {

    private static final String CONTROLLER_IDENTITY = "vespa.vespa.hosting";

    private static final String HOSTNAME = "myhostname";
    private static final String PROXY_HOSTNAME = "myproxyhostname";

    private static final String OPENSTACK_ID = "OPENSTACK-ID";
    private static final String AWS_INSTANCE_ID = "i-abcdef123456";

    private static final String INSTANCE_ID = "default";
    private static final Zone ZONE = new Zone(SystemName.main, Environment.prod, RegionName.defaultName());
    private static final KeyPair KEYPAIR = KeyUtils.generateKeypair(RSA);
    private static final X509Certificate ATHENZ_YAHOO_CA_CERT = createDummyCaCertificate("Yahoo Athenz CA");
    private static final X509Certificate ATHENZ_AWS_CA_CERT = createDummyCaCertificate("Athenz AWS CA");

    @Test
    public void accepts_configserver_selfsigned_cert() {
        NodeRepositoryTester nodeRepositoryDummy = new NodeRepositoryTester();
        X509Certificate certificate = X509CertificateBuilder
                .fromKeypair(
                        KEYPAIR, new X500Principal("CN=" + HOSTNAME), Instant.EPOCH, Instant.EPOCH.plusSeconds(60), SHA256_WITH_RSA, 1)
                .build();
        NodeIdentifier identifier = new NodeIdentifier(ZONE, nodeRepositoryDummy.nodeRepository());
        NodePrincipal identity = identifier.resolveNode(singletonList(certificate));
        assertTrue(identity.getHostname().isPresent());
        assertEquals(HOSTNAME, identity.getHostname().get());
        assertEquals(HOSTNAME, identity.getHostIdentityName());
    }

    @Test
    public void accepts_openstack_host_certificate() {
        NodeRepositoryTester nodeRepositoryDummy = new NodeRepositoryTester();
        nodeRepositoryDummy.addNode(OPENSTACK_ID, HOSTNAME, INSTANCE_ID, NodeType.host);
        nodeRepositoryDummy.setNodeState(HOSTNAME, Node.State.active);
        Pkcs10Csr csr = Pkcs10CsrBuilder
                .fromKeypair(new X500Principal("CN=" + TENANT_DOCKER_HOST_IDENTITY), KEYPAIR, SHA256_WITH_RSA)
                .build();
        X509Certificate certificate = X509CertificateBuilder
                .fromCsr(csr, ATHENZ_YAHOO_CA_CERT.getSubjectX500Principal(), Instant.EPOCH, Instant.EPOCH.plusSeconds(60), KEYPAIR.getPrivate(), SHA256_WITH_RSA, 1)
                .addSubjectAlternativeName(OPENSTACK_ID + ".instanceid.athenz.provider-name.ostk.yahoo.cloud")
                .build();
        NodeIdentifier identifier = new NodeIdentifier(ZONE, nodeRepositoryDummy.nodeRepository());
        NodePrincipal identity = identifier.resolveNode(singletonList(certificate));
        assertTrue(identity.getHostname().isPresent());
        assertEquals(HOSTNAME, identity.getHostname().get());
        assertEquals(TENANT_DOCKER_HOST_IDENTITY, identity.getHostIdentityName());
    }

    @Test
    public void accepts_aws_host_certificate() {
        NodeRepositoryTester nodeRepositoryDummy = new NodeRepositoryTester();
        nodeRepositoryDummy.addNode(AWS_INSTANCE_ID, HOSTNAME, INSTANCE_ID, NodeType.host);
        nodeRepositoryDummy.setNodeState(HOSTNAME, Node.State.active);
        Pkcs10Csr csr = Pkcs10CsrBuilder
                .fromKeypair(new X500Principal("CN=" + TENANT_DOCKER_HOST_IDENTITY), KEYPAIR, SHA256_WITH_RSA)
                .build();
        X509Certificate certificate = X509CertificateBuilder
                .fromCsr(csr, ATHENZ_AWS_CA_CERT.getSubjectX500Principal(), Instant.EPOCH, Instant.EPOCH.plusSeconds(60), KEYPAIR.getPrivate(), SHA256_WITH_RSA, 1)
                .addSubjectAlternativeName(AWS_INSTANCE_ID + ".instanceid.athenz.aws.oath.cloud")
                .build();
        NodeIdentifier identifier = new NodeIdentifier(ZONE, nodeRepositoryDummy.nodeRepository());
        NodePrincipal identity = identifier.resolveNode(singletonList(certificate));
        assertTrue(identity.getHostname().isPresent());
        assertEquals(HOSTNAME, identity.getHostname().get());
        assertEquals(TENANT_DOCKER_HOST_IDENTITY, identity.getHostIdentityName());
    }

    @Test
    public void accepts_aws_proxy_host_certificate() {
        NodeRepositoryTester nodeRepositoryDummy = new NodeRepositoryTester();
        nodeRepositoryDummy.addNode(AWS_INSTANCE_ID, PROXY_HOSTNAME, INSTANCE_ID, NodeType.proxyhost);
        nodeRepositoryDummy.setNodeState(PROXY_HOSTNAME, Node.State.active);
        Pkcs10Csr csr = Pkcs10CsrBuilder
                .fromKeypair(new X500Principal("CN=" + PROXY_HOST_IDENTITY), KEYPAIR, SHA256_WITH_RSA)
                .build();
        X509Certificate certificate = X509CertificateBuilder
                .fromCsr(csr, ATHENZ_AWS_CA_CERT.getSubjectX500Principal(), Instant.EPOCH, Instant.EPOCH.plusSeconds(60), KEYPAIR.getPrivate(), SHA256_WITH_RSA, 1)
                .addSubjectAlternativeName(AWS_INSTANCE_ID + ".instanceid.athenz.aws.oath.cloud")
                .build();
        NodeIdentifier identifier = new NodeIdentifier(ZONE, nodeRepositoryDummy.nodeRepository());
        NodePrincipal identity = identifier.resolveNode(singletonList(certificate));
        assertTrue(identity.getHostname().isPresent());
        assertEquals(PROXY_HOSTNAME, identity.getHostname().get());
        assertEquals(PROXY_HOST_IDENTITY, identity.getHostIdentityName());
    }

    @Test
    public void accepts_aws_configserver_host_certificate() {
        NodeRepositoryTester nodeRepositoryDummy = new NodeRepositoryTester();
        Pkcs10Csr csr = Pkcs10CsrBuilder
                .fromKeypair(new X500Principal("CN=" + CONFIGSERVER_HOST_IDENTITY), KEYPAIR, SHA256_WITH_RSA)
                .build();
        X509Certificate certificate = X509CertificateBuilder
                .fromCsr(csr, ATHENZ_AWS_CA_CERT.getSubjectX500Principal(), Instant.EPOCH, Instant.EPOCH.plusSeconds(60), KEYPAIR.getPrivate(), SHA256_WITH_RSA, 1)
                .addSubjectAlternativeName(AWS_INSTANCE_ID + ".instanceid.athenz.aws.oath.cloud")
                .build();
        NodeIdentifier identifier = new NodeIdentifier(ZONE, nodeRepositoryDummy.nodeRepository());
        NodePrincipal identity = identifier.resolveNode(singletonList(certificate));
        assertEquals(CONFIGSERVER_HOST_IDENTITY, identity.getHostIdentityName());
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
                .fromKeypair(new X500Principal("CN=" + TENANT_DOCKER_CONTAINER_IDENTITY), KEYPAIR, SHA256_WITH_RSA)
                .build();
        VespaUniqueInstanceId vespaUniqueInstanceId = new VespaUniqueInstanceId(clusterIndex, clusterId, INSTANCE_ID, application, tenant, region, environment, NODE);
        X509Certificate certificate = X509CertificateBuilder
                .fromCsr(csr, ATHENZ_YAHOO_CA_CERT.getSubjectX500Principal(), Instant.EPOCH, Instant.EPOCH.plusSeconds(60), KEYPAIR.getPrivate(), SHA256_WITH_RSA, 1)
                .addSubjectAlternativeName(vespaUniqueInstanceId.asDottedString() + ".instanceid.athenz.provider-name.vespa.yahoo.cloud")
                .build();
        NodeIdentifier identifier = new NodeIdentifier(ZONE, nodeRepositoryDummy.nodeRepository());
        NodePrincipal identity = identifier.resolveNode(singletonList(certificate));
        assertTrue(identity.getHostname().isPresent());
        assertEquals(HOSTNAME, identity.getHostname().get());
        assertEquals(TENANT_DOCKER_CONTAINER_IDENTITY, identity.getHostIdentityName());
    }

    @Test
    public void accepts_controller_certificate() {
        NodeRepositoryTester nodeRepositoryDummy = new NodeRepositoryTester();
        Pkcs10Csr csr = Pkcs10CsrBuilder
                .fromKeypair(new X500Principal("CN=" + CONTROLLER_IDENTITY), KEYPAIR, SHA256_WITH_RSA)
                .build();
        X509Certificate certificate = X509CertificateBuilder
                .fromCsr(csr, ATHENZ_YAHOO_CA_CERT.getSubjectX500Principal(), Instant.EPOCH, Instant.EPOCH.plusSeconds(60), KEYPAIR.getPrivate(), SHA256_WITH_RSA, 1)
                .build();
        NodeIdentifier identifier = new NodeIdentifier(ZONE, nodeRepositoryDummy.nodeRepository());
        NodePrincipal identity = identifier.resolveNode(singletonList(certificate));
        assertFalse(identity.getHostname().isPresent());
        assertEquals(CONTROLLER_IDENTITY, identity.getHostIdentityName());
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

    private static X509Certificate createDummyCaCertificate(String caCommonName) {
        KeyPair keyPair = KeyUtils.generateKeypair(RSA);
        return X509CertificateBuilder
                .fromKeypair(
                        keyPair, new X500Principal("CN=" + caCommonName), Instant.EPOCH, Instant.EPOCH.plusSeconds(60), SHA256_WITH_RSA, 1)
                .setBasicConstraints(true, true)
                .build();

    }

    private static FlavorsConfig createFlavourConfig() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("docker", 1., 2., 50, Flavor.Type.DOCKER_CONTAINER).cost(1);
        return b.build();
    }

}