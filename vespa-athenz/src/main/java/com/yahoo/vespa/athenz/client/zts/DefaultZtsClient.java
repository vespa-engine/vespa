// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts;

import com.yahoo.security.Pkcs10Csr;
import com.yahoo.vespa.athenz.api.AthenzAccessToken;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzResourceName;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.AwsRole;
import com.yahoo.vespa.athenz.api.AwsTemporaryCredentials;
import com.yahoo.vespa.athenz.api.AzureTemporaryCredentials;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.athenz.api.ZToken;
import com.yahoo.vespa.athenz.client.ErrorHandler;
import com.yahoo.vespa.athenz.client.common.ClientBase;
import com.yahoo.vespa.athenz.client.zms.bindings.AccessResponseEntity;
import com.yahoo.vespa.athenz.client.zts.bindings.AccessTokenResponseEntity;
import com.yahoo.vespa.athenz.client.zts.bindings.AwsTemporaryCredentialsResponseEntity;
import com.yahoo.vespa.athenz.client.zts.bindings.AzureTemporaryCredentialsResponseEntity;
import com.yahoo.vespa.athenz.client.zts.bindings.IdentityRefreshRequestEntity;
import com.yahoo.vespa.athenz.client.zts.bindings.IdentityResponseEntity;
import com.yahoo.vespa.athenz.client.zts.bindings.InstanceIdentityCredentials;
import com.yahoo.vespa.athenz.client.zts.bindings.InstanceRefreshInformation;
import com.yahoo.vespa.athenz.client.zts.bindings.InstanceRegisterInformation;
import com.yahoo.vespa.athenz.client.zts.bindings.RoleCertificateRequestEntity;
import com.yahoo.vespa.athenz.client.zts.bindings.RoleCertificateResponseEntity;
import com.yahoo.vespa.athenz.client.zts.bindings.RoleTokenResponseEntity;
import com.yahoo.vespa.athenz.client.zts.bindings.TemporaryCredentialsResponse;
import com.yahoo.vespa.athenz.client.zts.bindings.TenantDomainsResponseEntity;
import com.yahoo.vespa.athenz.client.zts.utils.IdentityCsrGenerator;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;


/**
 * Default implementation of {@link ZtsClient}
 *
 * @author bjorncs
 * @author mortent
 */
public class DefaultZtsClient extends ClientBase implements ZtsClient {

    private final URI ztsUrl;

    private DefaultZtsClient(URI ztsUrl, Supplier<SSLContext> sslContextSupplier, HostnameVerifier hostnameVerifier, ErrorHandler errorHandler) {
        super("vespa-zts-client", sslContextSupplier, ZtsClientException::new, hostnameVerifier, errorHandler);
        this.ztsUrl = addTrailingSlash(ztsUrl);
    }

    @Override
    public InstanceIdentity registerInstance(AthenzIdentity providerIdentity,
                                             AthenzIdentity instanceIdentity,
                                             String attestationData,
                                             Pkcs10Csr csr) {
        InstanceRegisterInformation payload =
                new InstanceRegisterInformation(providerIdentity, instanceIdentity, attestationData, csr);
        HttpUriRequest request = RequestBuilder.post()
                .setUri(ztsUrl.resolve("instance/"))
                .setEntity(toJsonStringEntity(payload))
                .build();
        return execute(request, this::getInstanceIdentity);
    }

    @Override
    public InstanceIdentity refreshInstance(AthenzIdentity providerIdentity,
                                            AthenzIdentity instanceIdentity,
                                            String instanceId,
                                            Pkcs10Csr csr) {
        InstanceRefreshInformation payload = new InstanceRefreshInformation(csr);
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
        return execute(request, this::getInstanceIdentity);
    }

    @Override
    public Identity getServiceIdentity(AthenzIdentity identity, String keyId, Pkcs10Csr csr) {
        return getServiceIdentity(identity, keyId, csr, Optional.empty());
    }

    public Identity getServiceIdentity(AthenzIdentity identity, String keyId, Pkcs10Csr csr, Optional<NToken> nToken) {
        URI uri = ztsUrl.resolve(String.format("instance/%s/%s/refresh", identity.getDomainName(), identity.getName()));
        RequestBuilder builder = RequestBuilder.post()
                                               .setUri(uri)
                                               .setEntity(toJsonStringEntity(new IdentityRefreshRequestEntity(csr, keyId)));
        nToken.ifPresent(n -> builder.setHeader("Athenz-Principal-Auth", n.getRawToken()));

        return execute(builder.build(), response -> {
            IdentityResponseEntity entity = readEntity(response, IdentityResponseEntity.class);
            return new Identity(entity.certificate(), entity.caCertificateBundle());
        });
    }

    @Override
    public Identity getServiceIdentity(AthenzIdentity identity, String keyId, KeyPair keyPair, String dnsSuffix) {
        Pkcs10Csr csr = new IdentityCsrGenerator(dnsSuffix).generateIdentityCsr(identity, keyPair);
        return getServiceIdentity(identity, keyId, csr);
    }

    @Override
    public ZToken getRoleToken(AthenzDomain domain, Duration expiry) {
        return getRoleToken(domain, null, expiry);
    }

    @Override
    public ZToken getRoleToken(AthenzRole athenzRole, Duration expiry) {
        return getRoleToken(athenzRole.domain(), athenzRole.roleName(), expiry);
    }

    private ZToken getRoleToken(AthenzDomain domain, String roleName, Duration expiry) {
        URI uri = ztsUrl.resolve(String.format("domain/%s/token", domain.getName()));
        RequestBuilder requestBuilder = RequestBuilder.get(uri)
                .addHeader("Content-Type", "application/json");
        if (roleName != null) {
            requestBuilder.addParameter("role", roleName);
        }
        requestBuilder.addParameter("maxExpiryTime", Long.toString(expiry.getSeconds()));
        HttpUriRequest request = requestBuilder.build();
        return execute(request, response -> {
            RoleTokenResponseEntity roleTokenResponseEntity = readEntity(response, RoleTokenResponseEntity.class);
            return roleTokenResponseEntity.token;
        });
    }

    @Override
    public AthenzAccessToken getAccessToken(AthenzDomain domain, List<AthenzIdentity> proxyPrincipals) {
        return this.getAccessTokenImpl(List.of(new AthenzResourceName(domain, "domain")), proxyPrincipals);
    }

    @Override
    public AthenzAccessToken getAccessToken(List<AthenzRole> athenzRole) {
        return getAccessToken(athenzRole, List.of());
    }

    @Override
    public AthenzAccessToken getAccessToken(List<AthenzRole> athenzRole, List<AthenzIdentity> proxyPrincipals) {
        List<AthenzResourceName> athenzResourceNames = athenzRole.stream()
                .map(AthenzRole::toResourceName)
                .toList();
        return this.getAccessTokenImpl(athenzResourceNames, proxyPrincipals);
    }

    private AthenzAccessToken getAccessTokenImpl(List<AthenzResourceName> resources, List<AthenzIdentity> proxyPrincipals) {
        URI uri = ztsUrl.resolve("oauth2/token");
        RequestBuilder requestBuilder = RequestBuilder.post(uri)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addParameter("grant_type", "client_credentials")
                .addParameter("scope", resources.stream().map(AthenzResourceName::toResourceNameString).collect(Collectors.joining(" ")));
        if (proxyPrincipals.size()>0) {
            String proxyPrincipalString = proxyPrincipals.stream()
                    .map(AthenzIdentity::spiffeUri)
                    .map(URI::toString)
                    .collect(Collectors.joining(","));
            requestBuilder.addParameter("proxy_principal_spiffe_uris", proxyPrincipalString);
        }
        HttpUriRequest request = requestBuilder.build();
        return execute(request, response -> {
            AccessTokenResponseEntity accessTokenResponseEntity = readEntity(response, AccessTokenResponseEntity.class);
            return accessTokenResponseEntity.accessToken();
        });
    }

    @Override
    public X509Certificate getRoleCertificate(AthenzRole role, Pkcs10Csr csr, Duration expiry) {
        RoleCertificateRequestEntity requestEntity = new RoleCertificateRequestEntity(csr, expiry);
        URI uri = ztsUrl.resolve("rolecert");
        HttpUriRequest request = RequestBuilder.post(uri)
                .setEntity(toJsonStringEntity(requestEntity))
                .build();
        return execute(request, response -> {
            RoleCertificateResponseEntity responseEntity = readEntity(response, RoleCertificateResponseEntity.class);
            return responseEntity.certificate;
        });
    }

    @Override
    public X509Certificate getRoleCertificate(AthenzRole role, Pkcs10Csr csr) {
        return getRoleCertificate(role, csr, null);
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
            return entity.tenantDomainNames.stream().map(AthenzDomain::new).toList();
        });
    }

    @Override
    public AwsTemporaryCredentials getAwsTemporaryCredentials(AthenzDomain athenzDomain, AwsRole awsRole, Duration duration, String externalId) {
        URI uri = ztsUrl.resolve(
                String.format("domain/%s/role/%s/creds", athenzDomain.getName(), awsRole.encodedName()));
        RequestBuilder requestBuilder = RequestBuilder.get(uri);

        // Add optional durationSeconds and externalId parameters
        Optional.ofNullable(duration).ifPresent(d -> requestBuilder.addParameter("durationSeconds", Long.toString(duration.getSeconds())));
        Optional.ofNullable(externalId).ifPresent(s -> requestBuilder.addParameter("externalId", s));

        HttpUriRequest request = requestBuilder.build();
        return execute(request, response -> {
            AwsTemporaryCredentialsResponseEntity entity = readEntity(response, AwsTemporaryCredentialsResponseEntity.class);
            return entity.credentials();
        });
    }

    @Override
    public AzureTemporaryCredentials getAzureTemporaryCredentials(AthenzRole athenzRole, String azureIdentityId, String azureTokenScope) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("athenzRoleName", athenzRole.roleName());
        attributes.put("azureClientId", Objects.requireNonNull(azureIdentityId));
        if (azureTokenScope != null) attributes.put("azureTokenScope", azureTokenScope);
        return getExternalTemporaryCredentials("azure", athenzRole.domain(), attributes, AzureTemporaryCredentialsResponseEntity.class);
    }

    @Override
    public AzureTemporaryCredentials getAzureTemporaryCredentials(AthenzRole athenzRole, String azureResourceGroup, String azureIdentityName, String azureTokenScope) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("athenzRoleName", athenzRole.roleName());
        attributes.put("azureResourceGroup", Objects.requireNonNull(azureResourceGroup));
        attributes.put("azureClientName", Objects.requireNonNull(azureIdentityName));
        if (azureTokenScope != null) attributes.put("azureTokenScope", azureTokenScope);
        return getExternalTemporaryCredentials("azure", athenzRole.domain(), attributes, AzureTemporaryCredentialsResponseEntity.class);
    }

    private <T, U extends TemporaryCredentialsResponse<T>> T getExternalTemporaryCredentials(String provider, AthenzDomain domain, Map<String, String> attributes, Class<U> responseClass) {
        URI uri = ztsUrl.resolve("external/%s/domain/%s/creds".formatted(provider, domain.getName()));
        RequestBuilder requestBuilder = RequestBuilder.post(uri)
                .setEntity(toJsonStringEntity(Map.of("clientId", "%s.%s".formatted(domain.getName(), provider), "attributes", attributes)));

        HttpUriRequest request = requestBuilder.build();
        return execute(request, response -> readEntity(response, responseClass)).credentials();
    }

    @Override
    public boolean hasAccess(AthenzResourceName resource, String action, AthenzIdentity identity) {
        URI uri = ztsUrl.resolve(String.format("access/%s/%s?principal=%s",
                                               action, resource.toResourceNameString(), identity.getFullName()));
        HttpUriRequest request = RequestBuilder.get()
                .setUri(uri)
                .build();
        return execute(request, response -> {
            AccessResponseEntity result = readEntity(response, AccessResponseEntity.class);
            return result.granted;
        });
    }

    private InstanceIdentity getInstanceIdentity(HttpResponse response) throws IOException {
        InstanceIdentityCredentials entity = readEntity(response, InstanceIdentityCredentials.class);
        return entity.getServiceToken() != null
                    ? new InstanceIdentity(entity.getX509Certificate(), new NToken(entity.getServiceToken()))
                    : new InstanceIdentity(entity.getX509Certificate());
    }

    private static URI addTrailingSlash(URI ztsUrl) {
        if (ztsUrl.getPath().endsWith("/"))
            return ztsUrl;
        else
            return URI.create(ztsUrl.toString() + '/');
    }
    public static class Builder {
        private final URI ztsUrl;
        private ErrorHandler errorHandler = ErrorHandler.empty();
        private HostnameVerifier hostnameVerifier = null;
        private Supplier<SSLContext> sslContextSupplier = null;

        public Builder(URI ztsUrl) {
            this.ztsUrl = ztsUrl;
        }

        public Builder withErrorHandler(ErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        public Builder withHostnameVerifier(HostnameVerifier hostnameVerifier) {
            this.hostnameVerifier = hostnameVerifier;
            return this;
        }

        public Builder withSslContext(SSLContext sslContext) {
            this.sslContextSupplier = () -> sslContext;
            return this;
        }

        public Builder withIdentityProvider(ServiceIdentityProvider identityProvider) {
            this.sslContextSupplier = identityProvider::getIdentitySslContext;
            return this;
        }

        public DefaultZtsClient build() {
            if (sslContextSupplier == null)
                throw new IllegalArgumentException("No SSL context or identity provider available to set up ZTS client");
            return new DefaultZtsClient(ztsUrl, sslContextSupplier, hostnameVerifier, errorHandler);
        }
    }
}
