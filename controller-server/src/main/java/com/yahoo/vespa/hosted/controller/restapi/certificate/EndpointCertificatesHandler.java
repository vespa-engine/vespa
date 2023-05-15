package com.yahoo.vespa.hosted.controller.restapi.certificate;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.RestApiException;
import com.yahoo.restapi.StringResponse;
import com.yahoo.vespa.hosted.controller.api.integration.ServiceRegistry;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateProvider;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateRequestMetadata;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
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

    public EndpointCertificatesHandler(Executor executor, ServiceRegistry serviceRegistry, CuratorDb curator) {
        super(executor);
        this.endpointCertificateProvider = serviceRegistry.endpointCertificateProvider();
        this.curator = curator;
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

        try (var lock = curator.lock(TenantAndApplicationId.from(applicationId))) {
            EndpointCertificateMetadata endpointCertificateMetadata = curator.readEndpointCertificateMetadata(applicationId)
                    .orElseThrow(() -> new RestApiException.NotFound("No certificate found for application " + applicationId.serializedForm()));

            EndpointCertificateMetadata reRequestedMetadata = endpointCertificateProvider.requestCaSignedCertificate(
                    applicationId, endpointCertificateMetadata.requestedDnsSans(), ignoreExistingMetadata ? Optional.empty() : Optional.of(endpointCertificateMetadata));

            curator.writeEndpointCertificateMetadata(applicationId, reRequestedMetadata);

            return new StringResponse(EndpointCertificateMetadataSerializer.toSlime(reRequestedMetadata).toString());
        }
    }
}
