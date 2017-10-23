// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.athenz.identityproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.IdentityDocumentGenerator;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.InstanceValidator;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.KeyProvider;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.Utils;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.IdentityDocument;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.InstanceConfirmation;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.ProviderUniqueId;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.SignedIdentityDocument;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.testutils.MockNodeFlavors;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.junit.Ignore;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Optional;
import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author bjorncs
 */
public class AthenzInstanceProviderServiceTest {

    private static final Logger log = Logger.getLogger(AthenzInstanceProviderServiceTest.class.getName());
    private static final int PORT = 12345;

    @Test
    @Ignore("Requires private key for Athenz service")
    public void provider_service_hosts_endpoint_secured_with_tls() throws Exception {
        String domain = "INSERT DOMAIN HERE";
        String service = "INSERT SERVICE NAME HERE";
        DummyKeyProvider keyProvider = new DummyKeyProvider();
        PrivateKey privateKey = Crypto.loadPrivateKey(keyProvider.getPrivateKey(0));

        AthenzProviderServiceConfig config = getAthenzProviderConfig(domain, service, "INSERT ZTS URL HERE", "INSERT DNS SUFFIX HERE");

        ScheduledExecutorServiceMock executor = new ScheduledExecutorServiceMock();
        NodeRepository nodeRepository = mock(NodeRepository.class);
        Zone zone = new Zone(Environment.dev, RegionName.from("us-north-1"));
        AthenzInstanceProviderService athenzInstanceProviderService = new AthenzInstanceProviderService(config, keyProvider, executor, nodeRepository, zone);

        try (CloseableHttpClient client = createHttpClient(domain, service)) {
            Runnable certificateRefreshCommand = executor.getCommand().orElseThrow(() -> new AssertionError("Command not present"));
            assertFalse(getStatus(client));
            certificateRefreshCommand.run();
            assertTrue(getStatus(client));
            assertInstanceConfirmationSucceeds(client, privateKey);
            certificateRefreshCommand.run();
            assertTrue(getStatus(client));
            assertInstanceConfirmationSucceeds(client, privateKey);
        } finally {
            athenzInstanceProviderService.deconstruct();
        }
    }

    @Test
    public void generates_valid_identity_document() throws IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        String hostname = "x.y.com";
        AutoGeneratedKeyProvider keyProvider = new AutoGeneratedKeyProvider();
        AthenzProviderServiceConfig config = getAthenzProviderConfig("domain", "service", "localhost/zts", "dnsSuffix");

        NodeRepository nodeRepository = mock(NodeRepository.class);
        MockNodeFlavors nodeFlavors = new MockNodeFlavors();
        ApplicationId appid = ApplicationId.from(TenantName.from("tenant"), ApplicationName.from("application"), InstanceName.from("default"));
        Allocation allocation = new Allocation(appid, ClusterMembership.from("container/default/0/0", Version.fromString("1.2.3")), Generation.inital(), false);        Flavor flavor = nodeFlavors.getFlavorOrThrow("default");
        Node n = Node.create("ostkid", ImmutableSet.of("127.0.0.1"), new HashSet<>(), hostname, Optional.empty(), flavor, NodeType.tenant).with(allocation);
        when(nodeRepository.getNode(eq(hostname))).thenReturn(Optional.of(n));
        Zone zone = new Zone(Environment.dev, RegionName.from("us-north-1"));

        IdentityDocumentGenerator identityDocumentGenerator = new IdentityDocumentGenerator(config, nodeRepository, zone, keyProvider);
        String rawSignedIdentityDocument = identityDocumentGenerator.generateSignedIdentityDocument(hostname);


        SignedIdentityDocument signedIdentityDocument = Utils.getMapper().readValue(rawSignedIdentityDocument, SignedIdentityDocument.class);

        // Verify attributes
        assertEquals(hostname, signedIdentityDocument.identityDocument.instanceHostname);
        ProviderUniqueId expectedProviderUniqueId = new ProviderUniqueId("tenant", "application", "dev", "us-north-1", "default", "default", 0);
        assertEquals(expectedProviderUniqueId, signedIdentityDocument.identityDocument.providerUniqueId);

        // Validate signature
        assertTrue("Message", InstanceValidator.isSignatureValid(Crypto.loadPublicKey(keyProvider.getPublicKey(0)), signedIdentityDocument.rawIdentityDocument, signedIdentityDocument.signature));

    }

    private AthenzProviderServiceConfig getAthenzProviderConfig(String domain, String service, String ztsUrl, String dnsSuffix) {
        return new AthenzProviderServiceConfig(
                        new AthenzProviderServiceConfig.Builder()
                                .domain(domain)
                                .serviceName(service)
                                .port(PORT)
                                .keyPathPrefix("dummy-path")
                                .certDnsSuffix(dnsSuffix)
                                .ztsUrl(ztsUrl)
                                .athenzPrincipalHeaderName("INSERT PRINCIPAL HEADER NAME HERE")
                                .apiPath("/"));

    }
    private static boolean getStatus(HttpClient client) {
        try {
            HttpResponse response = client.execute(new HttpGet("https://localhost:" + PORT + "/status.html"));
            return response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
        } catch (Exception e) {
            log.log(LogLevel.INFO, "Status.html failed: " + e);
            return false;
        }
    }

    private static void assertInstanceConfirmationSucceeds(HttpClient client, PrivateKey privateKey) throws IOException {
        HttpPost httpPost = new HttpPost("https://localhost:" + PORT + "/");
        httpPost.setEntity(createInstanceConfirmation(privateKey));
        HttpResponse response = client.execute(httpPost);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
    }

    private static CloseableHttpClient createHttpClient(String domain, String service)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException {
        SSLContext sslContext = new SSLContextBuilder()
                .loadTrustMaterial(null, (certificateChain, ignoredAuthType) ->
                        certificateChain[0].getSubjectX500Principal().getName().equals("CN=" + domain + "." + service))
                .build();

        return HttpClients.custom()
                .setSslcontext(sslContext)
                .setSSLHostnameVerifier(new NoopHostnameVerifier())
                .build();
    }

    private static HttpEntity createInstanceConfirmation(PrivateKey privateKey) {
        IdentityDocument identityDocument = new IdentityDocument(
                new ProviderUniqueId(
                        "tenant", "application", "environment", "region", "instance", "cluster-id", 0),
                "hostname", "instance-hostname", Instant.now());
        try {
            ObjectMapper mapper = Utils.getMapper();
            String encodedIdentityDocument =
                    Base64.getEncoder().encodeToString(mapper.writeValueAsString(identityDocument).getBytes());
            Signature sigGenerator = Signature.getInstance("SHA512withRSA");
            sigGenerator.initSign(privateKey);
            sigGenerator.update(encodedIdentityDocument.getBytes());
            String signature = Base64.getEncoder().encodeToString(sigGenerator.sign());

            InstanceConfirmation instanceConfirmation = new InstanceConfirmation(
                    "provider", "domain", "service",
                    new SignedIdentityDocument(encodedIdentityDocument, signature, 0, identityDocument.providerUniqueId.asString(), "dnssuffix", "service", "localhost/zts",1));
            return new StringEntity(mapper.writeValueAsString(instanceConfirmation));
        } catch (JsonProcessingException
                | NoSuchAlgorithmException
                | UnsupportedEncodingException
                | SignatureException
                | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private static class DummyKeyProvider implements KeyProvider {

        @Override
        public String getPrivateKey(int version) {
            return "INSERT PRIV KEY";
        }

        @Override
        public String getPublicKey(int version) {
            return "INSERT PUB KEY";
        }
    }

    private static class AutoGeneratedKeyProvider implements KeyProvider {

        private final String publicKey;
        private final String privateKey;

        public AutoGeneratedKeyProvider() throws IOException, NoSuchAlgorithmException {
            KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
            rsa.initialize(2048);
            KeyPair keyPair = rsa.genKeyPair();
            publicKey = pemEncode("RSA PUBLIC KEY", keyPair.getPublic());
            privateKey = pemEncode("RSA PRIVATE KEY", keyPair.getPrivate());
        }

        private String pemEncode(String description, Key key) throws IOException {
            StringWriter stringWriter = new StringWriter();
            JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter);
            pemWriter.writeObject(key);
            pemWriter.flush();
            return stringWriter.toString();

        }

        @Override
        public String getPrivateKey(int version) {
            return privateKey;
        }

        @Override
        public String getPublicKey(int version) {
            return publicKey;
        }
    }
}
