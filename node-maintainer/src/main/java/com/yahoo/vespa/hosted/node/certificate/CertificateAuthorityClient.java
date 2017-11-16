package com.yahoo.vespa.hosted.node.certificate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca.model.CertificateSerializedPayload;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca.model.CsrSerializedPayload;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

/**
 * Sends Certificate Signing Requests to config server
 *
 * @author freva
 */
class CertificateAuthorityClient {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final URI caServerUri;
    private final CloseableHttpClient client;

    CertificateAuthorityClient(String caServerHostname) {
        this.caServerUri = URI.create("https://" + caServerHostname + ":8443/athenz/v1/provider/sign");

        // Trust all certificates
        SSLConnectionSocketFactory sslSocketFactory;
        try {
            sslSocketFactory = new SSLConnectionSocketFactory(
                    new SSLContextBuilder().loadTrustMaterial(null, (arg0, arg1) -> true).build(),
                    SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        this.client = HttpClientBuilder.create()
                .setUserAgent("Node-Maintainer CA client")
                .setSSLSocketFactory(sslSocketFactory)
                .disableAutomaticRetries()
                .build();
    }

    X509Certificate signCsr(PKCS10CertificationRequest csr) {
        CsrSerializedPayload csrSerialized = new CsrSerializedPayload(csr);
        CertificateSerializedPayload certificateSerialized = sendCsr(csrSerialized);
        return certificateSerialized.certificate;
    }

    private CertificateSerializedPayload sendCsr(CsrSerializedPayload csrSerialized) {
        try {
            String csrJson = mapper.writeValueAsString(csrSerialized);
            InputStream certificateResponseStream = postJson(caServerUri, csrJson);
            return mapper.readValue(certificateResponseStream, CertificateSerializedPayload.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize CSR/deserialize certificate", e);
        }
    }

    private InputStream postJson(URI uri, String json) {
        HttpPost request = new HttpPost(uri);
        request.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");

        try {
            request.setEntity(new StringEntity(json));
            HttpResponse response = client.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != 200) {
                String responseBody = IOUtils.readAll(response.getEntity().getContent(), StandardCharsets.UTF_8);
                throw new RuntimeException("CA responded with HTTP-" + statusCode + ": " + responseBody);
            }
            return response.getEntity().getContent();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Failed to encode request content", e);
        } catch (IOException e) {
            throw new RuntimeException("Error executing request", e);
        }
    }
}
