// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca.restapi;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.log.LogLevel;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.Pkcs10Csr;
import com.yahoo.security.SubjectAlternativeName;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identityprovider.api.EntityBindingsMapper;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.instanceconfirmation.InstanceConfirmation;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.instanceconfirmation.InstanceValidator;
import com.yahoo.vespa.hosted.ca.Certificates;
import com.yahoo.vespa.hosted.ca.instance.InstanceIdentity;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

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
    private final InstanceValidator instanceValidator;

    @Inject
    public CertificateAuthorityApiHandler(Context ctx, SecretStore secretStore, AthenzProviderServiceConfig athenzProviderServiceConfig, InstanceValidator instanceValidator) {
        this(ctx, secretStore, new Certificates(Clock.systemUTC()), athenzProviderServiceConfig, instanceValidator);
    }

    CertificateAuthorityApiHandler(Context ctx, SecretStore secretStore, Certificates certificates, AthenzProviderServiceConfig athenzProviderServiceConfig, InstanceValidator instanceValidator) {
        super(ctx);
        this.secretStore = secretStore;
        this.certificates = certificates;
        this.caPrivateKeySecretName = athenzProviderServiceConfig.secretName();
        this.caCertificateSecretName = athenzProviderServiceConfig.domain() + ".ca.cert";
        this.instanceValidator = instanceValidator;
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

        InstanceConfirmation confirmation = new InstanceConfirmation(instanceRegistration.provider(), instanceRegistration.domain(), instanceRegistration.service(), EntityBindingsMapper.toSignedIdentityDocumentEntity(instanceRegistration.attestationData()));
        confirmation.set(InstanceValidator.SAN_IPS_ATTRNAME, getSubjectAlternativeNames(instanceRegistration.csr(), SubjectAlternativeName.Type.IP_ADDRESS));
        confirmation.set(InstanceValidator.SAN_DNS_ATTRNAME, getSubjectAlternativeNames(instanceRegistration.csr(), SubjectAlternativeName.Type.DNS_NAME));
        if (!instanceValidator.isValidInstance(confirmation)) {
            log.log(LogLevel.INFO, "Invalid instance registration for " + instanceRegistration.toString());
            return ErrorResponse.forbidden("Unable to launch service: " +instanceRegistration.service());
        }
        var certificate = certificates.create(instanceRegistration.csr(), caCertificate(), caPrivateKey());
        var instanceId = Certificates.instanceIdFrom(instanceRegistration.csr());
        var identity = new InstanceIdentity(instanceRegistration.provider(), instanceRegistration.service(), instanceId,
                                            Optional.of(certificate));
        return new SlimeJsonResponse(InstanceSerializer.identityToSlime(identity));
    }

    private String getSubjectAlternativeNames(Pkcs10Csr csr, SubjectAlternativeName.Type sanType) {
        return csr.getSubjectAlternativeNames().stream()
                .map(SubjectAlternativeName::decode)
                .filter(san -> san.getType() == sanType)
                .map(SubjectAlternativeName::getValue)
                .collect(Collectors.joining(","));
    }

    private HttpResponse refreshInstance(HttpRequest request, String provider, String service, String instanceId) {
        var instanceRefresh = deserializeRequest(request, InstanceSerializer::refreshFromSlime);
        var instanceIdFromCsr = Certificates.instanceIdFrom(instanceRefresh.csr());
        if (!instanceIdFromCsr.equals(instanceId)) {
            throw new IllegalArgumentException("Mismatch between instance ID in URL path and instance ID in CSR " +
                                               "[instanceId=" + instanceId + ",instanceIdFromCsr=" + instanceIdFromCsr +
                                               "]");
        }
        AthenzService athenzService = new AthenzService(request.getJDiscRequest().getUserPrincipal().getName());
        List<String> commonNames = X509CertificateUtils.getCommonNames(instanceRefresh.csr().getSubject());
        if(commonNames.size() != 1 && !Objects.equals(commonNames.get(0), athenzService.getFullName())) {
            throw new IllegalArgumentException(String.format("Invalid request, trying to refresh service %s using service %s.", instanceRefresh.csr().getSubject().getName(), athenzService.getFullName()));
        }
        InstanceConfirmation instanceConfirmation = new InstanceConfirmation(provider, athenzService.getDomain().getName(), athenzService.getName(), null);
        instanceConfirmation.set(InstanceValidator.SAN_IPS_ATTRNAME, getSubjectAlternativeNames(instanceRefresh.csr(), SubjectAlternativeName.Type.IP_ADDRESS));
        instanceConfirmation.set(InstanceValidator.SAN_DNS_ATTRNAME, getSubjectAlternativeNames(instanceRefresh.csr(), SubjectAlternativeName.Type.DNS_NAME));
        if(!instanceValidator.isValidRefresh(instanceConfirmation)) {
            return ErrorResponse.forbidden("Unable to refresh cert: " + instanceRefresh.csr().getSubject().toString());
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
