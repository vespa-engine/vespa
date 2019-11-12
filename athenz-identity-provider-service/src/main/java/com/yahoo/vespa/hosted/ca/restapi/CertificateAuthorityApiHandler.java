// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca.restapi;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.ca.Certificates;
import com.yahoo.vespa.hosted.ca.instance.InstanceIdentity;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * REST API for issuing and refreshing node certificates in a hosted Vespa system.
 *
 * The API implements the following subset of methods from the Athenz ZTS REST API:
 *
 * - Instance registration
 * - Instance refresh
 *
 * @author mpolden
 */
public class CertificateAuthorityApiHandler extends LoggingRequestHandler {

    private final SecretStore secretStore;
    private final Certificates certificates;
    private final String caPrivateKeySecretName;
    private final String caCertificateSecretName;

    @Inject
    public CertificateAuthorityApiHandler(Context ctx, SecretStore secretStore, AthenzProviderServiceConfig athenzProviderServiceConfig) {
        this(ctx, secretStore, new Certificates(Clock.systemUTC()), athenzProviderServiceConfig);
    }

    CertificateAuthorityApiHandler(Context ctx, SecretStore secretStore, Certificates certificates, AthenzProviderServiceConfig athenzProviderServiceConfig) {
        super(ctx);
        this.secretStore = secretStore;
        this.certificates = certificates;
        this.caPrivateKeySecretName = athenzProviderServiceConfig.secretName();
        this.caCertificateSecretName = athenzProviderServiceConfig.domain() + ".ca.cert";
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case POST: return handlePost(request);
                default: return ErrorResponse.methodNotAllowed("Method " + request.getMethod() + " is unsupported");
            }
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(request.getMethod() + " " + request.getUri() + " failed: " + Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling " + request.getMethod() + " " + request.getUri(), e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse handlePost(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/ca/v1/instance/")) return registerInstance(request);
        if (path.matches("/ca/v1/instance/{provider}/{domain}/{service}/{instanceId}")) return refreshInstance(request, path.get("provider"), path.get("service"), path.get("instanceId"));
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse registerInstance(HttpRequest request) {
        var instanceRegistration = deserializeRequest(request, InstanceSerializer::registrationFromSlime);
        var certificate = certificates.create(instanceRegistration.csr(), caCertificate(), caPrivateKey());
        var instanceId = Certificates.instanceIdFrom(instanceRegistration.csr());
        var identity = new InstanceIdentity(instanceRegistration.provider(), instanceRegistration.service(), instanceId,
                                            Optional.of(certificate));
        return new SlimeJsonResponse(InstanceSerializer.identityToSlime(identity));
    }

    private HttpResponse refreshInstance(HttpRequest request, String provider, String service, String instanceId) {
        var instanceRefresh = deserializeRequest(request, InstanceSerializer::refreshFromSlime);
        var instanceIdFromCsr = Certificates.instanceIdFrom(instanceRefresh.csr());
        if (!instanceIdFromCsr.equals(instanceId)) {
            throw new IllegalArgumentException("Mismatch between instance ID in URL path and instance ID in CSR " +
                                               "[instanceId=" + instanceId + ",instanceIdFromCsr=" + instanceIdFromCsr +
                                               "]");
        }
        var certificate = certificates.create(instanceRefresh.csr(), caCertificate(), caPrivateKey());
        var identity = new InstanceIdentity(provider, service, instanceIdFromCsr, Optional.of(certificate));
        return new SlimeJsonResponse(InstanceSerializer.identityToSlime(identity));
    }

    /** Returns CA certificate from secret store */
    private X509Certificate caCertificate() {
        return X509CertificateUtils.fromPem(secretStore.getSecret(caCertificateSecretName));
    }

    /** Returns CA private key from secret store */
    private PrivateKey caPrivateKey() {
        return KeyUtils.fromPemEncodedPrivateKey(secretStore.getSecret(caPrivateKeySecretName));
    }

    private static <T> T deserializeRequest(HttpRequest request, Function<Slime, T> serializer) {
        try {
            var slime = SlimeUtils.jsonToSlime(request.getData().readAllBytes());
            return serializer.apply(slime);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
