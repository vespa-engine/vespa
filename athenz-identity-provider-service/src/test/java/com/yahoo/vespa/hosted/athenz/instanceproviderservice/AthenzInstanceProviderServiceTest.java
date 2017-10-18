// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.athenz.identityproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.KeyProvider;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.Utils;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.IdentityDocument;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.InstanceConfirmation;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.ProviderUniqueId;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.SignedIdentityDocument;
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
import org.junit.Ignore;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.time.Instant;
import java.util.Base64;
import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

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

        AthenzProviderServiceConfig config =
                new AthenzProviderServiceConfig(
                        new AthenzProviderServiceConfig.Builder()
                                .domain(domain)
                                .serviceName(service)
                                .port(PORT)
                                .keyPathPrefix("dummy-path")
                                .certDnsSuffix("INSERT DNS SUFFIX HERE")
                                .ztsUrl("INSERT ZTS URL HERE")
                                .athenzPrincipalHeaderName("INSERT PRINCIPAL HEADER NAME HERE")
                                .apiPath("/"));

        ScheduledExecutorServiceMock executor = new ScheduledExecutorServiceMock();
        AthenzInstanceProviderService athenzInstanceProviderService = new AthenzInstanceProviderService(config, keyProvider, executor);

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
                "domain", "service",
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
                    new SignedIdentityDocument(encodedIdentityDocument, signature, 0, 1));
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
}
