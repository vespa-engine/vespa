package com.yahoo.vespa.hosted.controller.restapi.certificate;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.RestApiException;
import com.yahoo.restapi.StringResponse;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.ServiceRegistry;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateProvider;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateRequestMetadata;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.certificate.AssignedCertificate;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.persistence.EndpointCertificateMetadataSerializer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static com.yahoo.jdisc.http.HttpRequest.Method.POST;

/**
 * List all certificate requests for a system, with their requested DNS names.
 * Used for debugging, and verifying basic functionality of Cameo client in CD.
 *
 * @author andreer
 */

public class EndpointCertificatesHandler extends ThreadedHttpRequestHandler {

    private final EndpointCertificateProvider endpointCertificateProvider;
    private final CuratorDb curator;
    private final BooleanFlag useAlternateCertProvider;
    private final StringFlag endpointCertificateAlgo;
    private final BooleanFlag useRandomizedCert;

    public EndpointCertificatesHandler(Executor executor, ServiceRegistry serviceRegistry, CuratorDb curator, Controller controller) {
        super(executor);
        this.endpointCertificateProvider = serviceRegistry.endpointCertificateProvider();
        this.curator = curator;
        this.useAlternateCertProvider = PermanentFlags.USE_ALTERNATIVE_ENDPOINT_CERTIFICATE_PROVIDER.bindTo(controller.flagSource());
        this.endpointCertificateAlgo = PermanentFlags.ENDPOINT_CERTIFICATE_ALGORITHM.bindTo(controller.flagSource());
        this.useRandomizedCert = Flags.RANDOMIZED_ENDPOINT_NAMES.bindTo(controller.flagSource());
    }

    public HttpResponse handle(HttpRequest request) {
        if (request.getMethod().equals(GET)) return listEndpointCertificates();
        if (request.getMethod().equals(POST)) return reRequestEndpointCertificateFor(request.getProperty("application"), request.getProperty("ignoreExistingMetadata") != null);
        throw new RestApiException.MethodNotAllowed(request);
    }

    public HttpResponse listEndpointCertificates() {
        List<EndpointCertificateRequestMetadata> endpointCertificateMetadata = endpointCertificateProvider.listCertificates();

        String requestsWithNames = endpointCertificateMetadata.stream()
                .map(metadata -> metadata.requestId() + " : " +
                        String.join(", ", metadata.dnsNames().stream()
                                .map(dnsNameStatus -> dnsNameStatus.dnsName)
                                .collect(Collectors.joining(", "))))
                .collect(Collectors.joining("\n"));

        return new StringResponse(requestsWithNames);
    }

    public StringResponse reRequestEndpointCertificateFor(String instanceId, boolean ignoreExistingMetadata) {
        ApplicationId applicationId = ApplicationId.fromFullString(instanceId);
        if (useRandomizedCert.with(FetchVector.Dimension.APPLICATION_ID, instanceId).value()) {
            throw new IllegalArgumentException("Cannot re-request certificate. " + instanceId + " is assigned certificate from a pool");
        }
        try (var lock = curator.lock(TenantAndApplicationId.from(applicationId))) {
            AssignedCertificate assignedCertificate = curator.readAssignedCertificate(applicationId)
                                                             .orElseThrow(() -> new RestApiException.NotFound("No certificate found for application " + applicationId.serializedForm()));

            String algo = this.endpointCertificateAlgo.with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm()).value();
            boolean useAlternativeProvider = useAlternateCertProvider.with(FetchVector.Dimension.APPLICATION_ID, applicationId.serializedForm()).value();
            String keyPrefix = applicationId.toFullString();

            EndpointCertificateMetadata reRequestedMetadata = endpointCertificateProvider.requestCaSignedCertificate(
                    keyPrefix, assignedCertificate.certificate().requestedDnsSans(),
                    ignoreExistingMetadata ?
                            Optional.empty() :
                            Optional.of(assignedCertificate.certificate()),
                    algo, useAlternativeProvider);

            curator.writeAssignedCertificate(assignedCertificate.with(reRequestedMetadata));

            return new StringResponse(EndpointCertificateMetadataSerializer.toSlime(reRequestedMetadata).toString());
        }
    }
}
