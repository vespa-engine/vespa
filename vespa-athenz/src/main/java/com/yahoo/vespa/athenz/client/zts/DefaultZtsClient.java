// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.athenz.api.ZToken;
import com.yahoo.vespa.athenz.client.zts.bindings.ErrorResponseEntity;
import com.yahoo.vespa.athenz.client.zts.bindings.IdentityRefreshRequestEntity;
import com.yahoo.vespa.athenz.client.zts.bindings.IdentityResponseEntity;
import com.yahoo.vespa.athenz.client.zts.bindings.InstanceIdentityCredentials;
import com.yahoo.vespa.athenz.client.zts.bindings.InstanceRefreshInformation;
import com.yahoo.vespa.athenz.client.zts.bindings.InstanceRegisterInformation;
import com.yahoo.vespa.athenz.client.zts.bindings.RoleCertificateRequestEntity;
import com.yahoo.vespa.athenz.client.zts.bindings.RoleCertificateResponseEntity;
import com.yahoo.vespa.athenz.client.zts.bindings.RoleTokenResponseEntity;
import com.yahoo.vespa.athenz.client.zts.bindings.TenantDomainsResponseEntity;
import com.yahoo.vespa.athenz.client.zts.utils.IdentityCsrGenerator;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.identity.ServiceIdentitySslSocketFactory;
import com.yahoo.vespa.athenz.tls.Pkcs10Csr;
import com.yahoo.vespa.athenz.tls.Pkcs10CsrBuilder;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.jetty.http.HttpStatus;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static com.yahoo.vespa.athenz.tls.SignatureAlgorithm.SHA256_WITH_RSA;
import static com.yahoo.vespa.athenz.tls.SubjectAlternativeName.Type.DNS_NAME;
import static com.yahoo.vespa.athenz.tls.SubjectAlternativeName.Type.RFC822_NAME;
import static java.util.stream.Collectors.toList;

/**
 * Default implementation of {@link ZtsClient}
 *
 * @author bjorncs
 * @author mortent
 */
public class DefaultZtsClient implements ZtsClient {

    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final URI ztsUrl;
    private final CloseableHttpClient client;
    private final AthenzIdentity identity;

    public DefaultZtsClient(URI ztsUrl, AthenzIdentity identity, SSLContext sslContext) {
        this(ztsUrl, identity, () -> sslContext);
    }

    public DefaultZtsClient(URI ztsUrl, ServiceIdentityProvider identityProvider) {
        this(ztsUrl, identityProvider.identity(), identityProvider::getIdentitySslContext);
    }

    private DefaultZtsClient(URI ztsUrl, AthenzIdentity identity, Supplier<SSLContext> sslContextSupplier) {
        this.ztsUrl = addTrailingSlash(ztsUrl);
        this.identity = identity;
        this.client = createHttpClient(sslContextSupplier);
    }

    @Override
    public InstanceIdentity registerInstance(AthenzService providerIdentity,
                                             AthenzService instanceIdentity,
                                             String instanceId,
                                             String attestationData,
                                             boolean requestServiceToken,
                                             Pkcs10Csr csr) {
        InstanceRegisterInformation payload =
                new InstanceRegisterInformation(providerIdentity, instanceIdentity, attestationData, csr, requestServiceToken);
        HttpUriRequest request = RequestBuilder.post()
                .setUri(ztsUrl.resolve("instance/"))
                .setEntity(toJsonStringEntity(payload))
                .build();
        return execute(request, DefaultZtsClient::getInstanceIdentity);
    }

    @Override
    public InstanceIdentity refreshInstance(AthenzService providerIdentity,
                                            AthenzService instanceIdentity,
                                            String instanceId,
                                            boolean requestServiceToken,
                                            Pkcs10Csr csr) {
        InstanceRefreshInformation payload = new InstanceRefreshInformation(csr, requestServiceToken);
        URI uri = ztsUrl.resolve(
                String.format("instance/%s/%s/%s/%s",
                              providerIdentity.getFullName(),
                              instanceIdentity.getDomain().getName(),
                              instanceIdentity.getName(),
                              instanceId));
        HttpUriRequest request = RequestBuilder.post()
                .setUri(uri)
                .setEntity(toJsonStringEntity(payload))
                .build();
        return execute(request, DefaultZtsClient::getInstanceIdentity);
    }

    @Override
    public Identity getServiceIdentity(AthenzService identity, String keyId, Pkcs10Csr csr) {
        URI uri = ztsUrl.resolve(String.format("instance/%s/%s/refresh", identity.getDomainName(), identity.getName()));
        HttpUriRequest request = RequestBuilder.post()
                .setUri(uri)
                .setEntity(toJsonStringEntity(new IdentityRefreshRequestEntity(csr, keyId)))
                .build();
        return execute(request, response -> {
            IdentityResponseEntity entity = readEntity(response, IdentityResponseEntity.class);
            return new Identity(entity.certificate(), entity.caCertificateBundle());
        });
    }

    @Override
    public Identity getServiceIdentity(AthenzService identity, String keyId, KeyPair keyPair, String dnsSuffix) {
        Pkcs10Csr csr = new IdentityCsrGenerator(dnsSuffix).generateIdentityCsr(identity, keyPair);
        return getServiceIdentity(identity, keyId, csr);
    }

    @Override
    public ZToken getRoleToken(AthenzDomain domain) {
        return getRoleToken(domain, null);
    }

    @Override
    public ZToken getRoleToken(AthenzRole athenzRole) {
        return getRoleToken(athenzRole.domain(), athenzRole.roleName());
    }

    private ZToken getRoleToken(AthenzDomain domain, String roleName) {
        URI uri = ztsUrl.resolve(String.format("domain/%s/token", domain.getName()));
        RequestBuilder requestBuilder = RequestBuilder.get(uri)
                .addHeader("Content-Type", "application/json");
        if (roleName != null) {
            requestBuilder.addParameter("role", roleName);
        }
        HttpUriRequest request = requestBuilder.build();
        return execute(request, response -> {
            RoleTokenResponseEntity roleTokenResponseEntity = readEntity(response, RoleTokenResponseEntity.class);
            return roleTokenResponseEntity.token;
        });
    }

    @Override
    public X509Certificate getRoleCertificate(AthenzRole role,
                                              Duration expiry,
                                              KeyPair keyPair,
                                              String cloud) {
        X500Principal principal = new X500Principal(String.format("cn=%s:role.%s", role.domain().getName(), role.roleName()));
        Pkcs10Csr csr = Pkcs10CsrBuilder.fromKeypair(principal, keyPair, SHA256_WITH_RSA)
                .addSubjectAlternativeName(DNS_NAME, String.format("%s.%s.%s", identity.getName(), identity.getDomainName().replace('.', '-'), cloud))
                .addSubjectAlternativeName(RFC822_NAME, String.format("%s.%s@%s", identity.getDomainName(), identity.getName(), cloud))
                .build();
        RoleCertificateRequestEntity requestEntity = new RoleCertificateRequestEntity(csr, expiry);
        URI uri = ztsUrl.resolve(String.format("domain/%s/role/%s/token", role.domain().getName(), role.roleName()));
        HttpUriRequest request = RequestBuilder.post(uri)
                .setEntity(toJsonStringEntity(requestEntity))
                .build();
        return execute(request, response -> {
            RoleCertificateResponseEntity responseEntity = readEntity(response, RoleCertificateResponseEntity.class);
            return responseEntity.certificate;
        });
    }

    @Override
    public X509Certificate getRoleCertificate(AthenzRole role,
                                              KeyPair keyPair,
                                              String cloud) {
        return getRoleCertificate(role, null, keyPair, cloud);
    }

    @Override
    public List<AthenzDomain> getTenantDomains(AthenzIdentity providerIdentity, AthenzIdentity userIdentity, String roleName) {
        URI uri = ztsUrl.resolve(
                String.format("providerdomain/%s/user/%s", providerIdentity.getDomainName(), userIdentity.getFullName()));
        HttpUriRequest request = RequestBuilder.get(uri)
                .addParameter("roleName", roleName)
                .addParameter("serviceName", providerIdentity.getName())
                .build();
        return execute(request, response -> {
            TenantDomainsResponseEntity entity = readEntity(response, TenantDomainsResponseEntity.class);
            return entity.tenantDomainNames.stream().map(AthenzDomain::new).collect(toList());
        });
    }

    private <T> T execute(HttpUriRequest request, ResponseHandler<T> responseHandler) {
        try {
            return client.execute(request, responseHandler);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static InstanceIdentity getInstanceIdentity(HttpResponse response) throws IOException {
        InstanceIdentityCredentials entity = readEntity(response, InstanceIdentityCredentials.class);
        return entity.getServiceToken() != null
                    ? new InstanceIdentity(entity.getX509Certificate(), new NToken(entity.getServiceToken()))
                    : new InstanceIdentity(entity.getX509Certificate());
    }

    private static <T> T readEntity(HttpResponse response, Class<T> entityType) throws IOException {
        if (HttpStatus.isSuccess(response.getStatusLine().getStatusCode())) {
            return objectMapper.readValue(response.getEntity().getContent(), entityType);
        } else {
            ErrorResponseEntity errorEntity = objectMapper.readValue(response.getEntity().getContent(), ErrorResponseEntity.class);
            throw new ZtsClientException(errorEntity.code, errorEntity.description);
        }
    }

    private static URI addTrailingSlash(URI ztsUrl) {
        if (ztsUrl.getPath().endsWith("/"))
            return ztsUrl;
        else
            return URI.create(ztsUrl.toString() + '/');
    }

    private static StringEntity toJsonStringEntity(Object entity) {
        try {
            return new StringEntity(objectMapper.writeValueAsString(entity), ContentType.APPLICATION_JSON);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static CloseableHttpClient createHttpClient(Supplier<SSLContext> sslContextSupplier) {
        return HttpClientBuilder.create()
                .setRetryHandler(new DefaultHttpRequestRetryHandler(3, /*requestSentRetryEnabled*/true))
                .setUserAgent("vespa-zts-client")
                .setSSLSocketFactory(new SSLConnectionSocketFactory(new ServiceIdentitySslSocketFactory(sslContextSupplier), (HostnameVerifier)null))
                .setDefaultRequestConfig(RequestConfig.custom()
                                                 .setConnectTimeout((int)Duration.ofSeconds(10).toMillis())
                                                 .setConnectionRequestTimeout((int)Duration.ofSeconds(10).toMillis())
                                                 .setSocketTimeout((int)Duration.ofSeconds(20).toMillis())
                                                 .build())
                .build();
    }

    @Override
    public void close() {
        try {
            this.client.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
