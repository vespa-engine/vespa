// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.api.AwsRole;
import com.yahoo.vespa.athenz.api.AwsTemporaryCredentials;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.athenz.api.ZToken;
import com.yahoo.vespa.athenz.client.common.ClientBase;
import com.yahoo.vespa.athenz.client.zts.bindings.AwsTemporaryCredentialsResponseEntity;
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
import com.yahoo.security.Pkcs10Csr;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;

/**
 * Default implementation of {@link ZtsClient}
 *
 * @author bjorncs
 * @author mortent
 */
public class DefaultZtsClient extends ClientBase implements ZtsClient {

    private final URI ztsUrl;
    private final AthenzIdentity identity;

    public DefaultZtsClient(URI ztsUrl, AthenzIdentity identity, SSLContext sslContext) {
        this(ztsUrl, identity, () -> sslContext);
    }

    public DefaultZtsClient(URI ztsUrl, ServiceIdentityProvider identityProvider) {
        this(ztsUrl, identityProvider.identity(), identityProvider::getIdentitySslContext);
    }

    private DefaultZtsClient(URI ztsUrl, AthenzIdentity identity, Supplier<SSLContext> sslContextSupplier) {
        super("vespa-zts-client", sslContextSupplier, ZtsClientException::new);
        this.ztsUrl = addTrailingSlash(ztsUrl);
        this.identity = identity;
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
        return execute(request, this::getInstanceIdentity);
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
        return execute(request, this::getInstanceIdentity);
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
    public X509Certificate getRoleCertificate(AthenzRole role, Pkcs10Csr csr, Duration expiry) {
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
            return entity.tenantDomainNames.stream().map(AthenzDomain::new).collect(toList());
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

}
