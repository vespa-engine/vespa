// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca.restapi;

import com.yahoo.component.annotation.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.jdisc.http.server.jetty.RequestUtils;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SubjectAlternativeName;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identityprovider.api.EntityBindingsMapper;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.InstanceConfirmation;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.InstanceValidator;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.ca.Certificates;
import com.yahoo.vespa.hosted.ca.instance.InstanceIdentity;
import com.yahoo.vespa.hosted.ca.instance.InstanceRefresh;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
public class CertificateAuthorityApiHandler extends ThreadedHttpRequestHandler {

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
        this.caCertificateSecretName = athenzProviderServiceConfig.caCertSecretName();
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
        confirmation.set(InstanceValidator.SAN_IPS_ATTRNAME, Certificates.getSubjectAlternativeNames(instanceRegistration.csr(), SubjectAlternativeName.Type.IP));
        confirmation.set(InstanceValidator.SAN_DNS_ATTRNAME, Certificates.getSubjectAlternativeNames(instanceRegistration.csr(), SubjectAlternativeName.Type.DNS));
        if (!instanceValidator.isValidInstance(confirmation)) {
            log.log(Level.INFO, "Invalid instance registration for " + instanceRegistration.toString());
            return ErrorResponse.forbidden("Unable to launch service: " +instanceRegistration.service());
        }
        var certificate = certificates.create(instanceRegistration.csr(), caCertificate(), caPrivateKey());
        var instanceId = Certificates.instanceIdFrom(instanceRegistration.csr());
        var identity = new InstanceIdentity(instanceRegistration.provider(), instanceRegistration.service(), instanceId,
                                            Optional.of(certificate));
        return new SlimeJsonResponse(InstanceSerializer.identityToSlime(identity));
    }

    private HttpResponse refreshInstance(HttpRequest request, String provider, String service, String instanceId) {
        var instanceRefresh = deserializeRequest(request, InstanceSerializer::refreshFromSlime);
        var instanceIdFromCsr = Certificates.instanceIdFrom(instanceRefresh.csr());

        var athenzService = getRequestAthenzService(request);

        if (!instanceIdFromCsr.equals(instanceId)) {
            throw new IllegalArgumentException("Mismatch between instance ID in URL path and instance ID in CSR " +
                                               "[instanceId=" + instanceId + ",instanceIdFromCsr=" + instanceIdFromCsr +
                                               "]");
        }

        // Verify that the csr instance id matches one of the certificates in the chain
        refreshesSameInstanceId(instanceIdFromCsr, request);


        // Validate that there is no privilege escalation (can only refresh same service)
        refreshesSameService(instanceRefresh, athenzService);

        InstanceConfirmation instanceConfirmation = new InstanceConfirmation(provider, athenzService.getDomain().getName(), athenzService.getName(), null);
        instanceConfirmation.set(InstanceValidator.SAN_IPS_ATTRNAME, Certificates.getSubjectAlternativeNames(instanceRefresh.csr(), SubjectAlternativeName.Type.IP));
        instanceConfirmation.set(InstanceValidator.SAN_DNS_ATTRNAME, Certificates.getSubjectAlternativeNames(instanceRefresh.csr(), SubjectAlternativeName.Type.DNS));
        if(!instanceValidator.isValidRefresh(instanceConfirmation)) {
            return ErrorResponse.forbidden("Unable to refresh cert: " + instanceRefresh.csr().getSubject().toString());
        }

        var certificate = certificates.create(instanceRefresh.csr(), caCertificate(), caPrivateKey());
        var identity = new InstanceIdentity(provider, service, instanceIdFromCsr, Optional.of(certificate));
        return new SlimeJsonResponse(InstanceSerializer.identityToSlime(identity));
    }

    public void refreshesSameInstanceId(String csrInstanceId, HttpRequest request) {
        String certificateInstanceId = getRequestCertificateChain(request).stream()
                .map(Certificates::instanceIdFrom)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findAny().orElseThrow(() -> new IllegalArgumentException("No client certificate with instance id in request."));

        if(! Objects.equals(certificateInstanceId, csrInstanceId)) {
            throw new IllegalArgumentException("Mismatch between instance ID in client certificate and instance ID in CSR " +
                                               "[instanceId=" + certificateInstanceId + ",instanceIdFromCsr=" + csrInstanceId +
                                               "]");
        }
    }

    private void refreshesSameService(InstanceRefresh instanceRefresh, AthenzService athenzService) {
        List<String> commonNames = X509CertificateUtils.getCommonNames(instanceRefresh.csr().getSubject());
        if(commonNames.size() != 1 && !Objects.equals(commonNames.get(0), athenzService.getFullName())) {
            throw new IllegalArgumentException(String.format("Invalid request, trying to refresh service %s using service %s.", instanceRefresh.csr().getSubject().getName(), athenzService.getFullName()));
        }
    }

    /** Returns CA certificate from secret store */
    private X509Certificate caCertificate() {
        return X509CertificateUtils.fromPem(secretStore.getSecret(caCertificateSecretName));
    }

    private List<X509Certificate> getRequestCertificateChain(HttpRequest request) {
        return Optional.ofNullable(request.getJDiscRequest().context().get(RequestUtils.JDISC_REQUEST_X509CERT))
                .map(X509Certificate[].class::cast)
                .map(Arrays::asList)
                .orElse(Collections.emptyList());
    }

    private AthenzService getRequestAthenzService(HttpRequest request) {
        return getRequestCertificateChain(request).stream()
                .findFirst()
                .flatMap(X509CertificateUtils::getSubjectCommonName)
                .map(AthenzService::new)
                .orElseThrow(() -> new RuntimeException("No certificate found"));
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
