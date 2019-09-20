// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca.restapi;

import com.google.inject.Inject;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.ca.Certificates;
import com.yahoo.vespa.hosted.ca.instance.InstanceIdentity;
import com.yahoo.vespa.hosted.provision.restapi.v2.ErrorResponse;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Optional;
import java.util.logging.Level;

/**
 * REST API for issuing and refreshing node certificates in a hosted Vespa system.
 *
 * The API implements the following subset of methods from the Athenz ZTS REST API:
 * - Instance registration
 * - Instance refresh
 *
 * @author mpolden
 */
public class CertificateAuthorityApiHandler extends LoggingRequestHandler {

    private final SecretStore secretStore;
    private final Certificates certificates;
    private final SystemName system;

    @Inject
    public CertificateAuthorityApiHandler(Context ctx, SecretStore secretStore, Zone zone) {
        this(ctx, secretStore, new Certificates(Clock.systemUTC()), zone.system());
    }

    CertificateAuthorityApiHandler(Context ctx, SecretStore secretStore, Certificates certificates, SystemName system) {
        super(ctx);
        this.secretStore = secretStore;
        this.certificates = certificates;
        this.system = system;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case POST: return handlePost(request);
                default: return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is unsupported");
            }
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse handlePost(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/ca/v1/instance/")) return registerInstance(request);
        // TODO: Implement refresh
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse registerInstance(HttpRequest request) {
        var body = slimeFromRequest(request);
        var instanceRegistration = InstanceSerializer.registrationFromSlime(body);
        var certificate = certificates.create(instanceRegistration.csr(), caCertificate(), caPrivateKey());
        var instanceId = Certificates.extractDnsName(instanceRegistration.csr());
        var identity = new InstanceIdentity(instanceRegistration.provider(), instanceRegistration.service(), instanceId,
                                            Optional.of(certificate));
        return new SlimeJsonResponse(InstanceSerializer.identityToSlime(identity));
    }

    /** Returns CA certificate from secret store */
    private X509Certificate caCertificate() {
        var keyName = String.format("vespa.external.%s.configserver.ca.cert.cert", system.value().toLowerCase());
        return X509CertificateUtils.fromPem(secretStore.getSecret(keyName));
    }

    /** Returns CA private key from secret store */
    private PrivateKey caPrivateKey() {
        var keyName = String.format("vespa.external.%s.configserver.ca.key.key", system.value().toLowerCase());
        return KeyUtils.fromPemEncodedPrivateKey(secretStore.getSecret(keyName));
    }

    private static Slime slimeFromRequest(HttpRequest request) {
        try {
            return SlimeUtils.jsonToSlime(request.getData().readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
