// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.athenz.zts.RoleCertificateRequest;
import com.yahoo.athenz.zts.RoleToken;
import com.yahoo.athenz.zts.ZTSClient;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.api.ZToken;
import com.yahoo.vespa.athenz.client.zts.DefaultZtsClient;
import com.yahoo.vespa.athenz.tls.X509CertificateUtils;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * @author mortent
 * @author bjorncs
 * @deprecated Will be replaced by {@link DefaultZtsClient} once role token/certificate caching is ready.
 */
@Deprecated
class ZtsClient {

    ZToken getRoleToken(AthenzDomain domain,
                        URI ztsEndpoint,
                        SSLContext sslContext) {
         // TODO ztsEndpoint should contain '/zts/v1' as path
        URI correctedZtsEndpoint = ztsEndpoint.resolve("/zts/v1");
        return new ZToken(
                new ZTSClient(correctedZtsEndpoint.toString(), sslContext)
                        .getRoleToken(domain.getName()).getToken());
    }

    ZToken getRoleToken(AthenzDomain domain,
                        String roleName,
                        URI ztsEndpoint,
                        SSLContext sslContext) {
        // TODO ztsEndpoint should contain '/zts/v1' as path
        URI correctedZtsEndpoint = ztsEndpoint.resolve("/zts/v1");
        return new ZToken(
                new ZTSClient(correctedZtsEndpoint.toString(), sslContext)
                        .getRoleToken(domain.getName(), roleName).getToken());
    }

    X509Certificate getRoleCertificate(AthenzRole role,
                                       String dnsSuffix,
                                       URI ztsEndpoint,
                                       AthenzService identity,
                                       PrivateKey privateKey,
                                       SSLContext sslContext) {
        // TODO ztsEndpoint should contain '/zts/v1' as path
        URI correctedZtsEndpoint = ztsEndpoint.resolve("/zts/v1");
        ZTSClient ztsClient = new ZTSClient(correctedZtsEndpoint.toString(), sslContext);
        RoleCertificateRequest rcr = ZTSClient.generateRoleCertificateRequest(
                identity.getDomain().getName(), identity.getName(), role.domain().getName(), role.roleName(), privateKey, dnsSuffix, (int) Duration.ofHours(1).getSeconds());
        RoleToken pemCert = ztsClient.postRoleCertificateRequest(role.domain().getName(), role.roleName(), rcr);
        return X509CertificateUtils.fromPem(pemCert.token);
    }

}
