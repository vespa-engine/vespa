package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.http.Client;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public class CertificateRemovalChangeValidator implements ChangeValidator {
    @Override
    public List<ConfigChangeAction> validate(VespaModel current, VespaModel next, ValidationOverrides overrides, Instant now) {

        current.getContainerClusters()
                .forEach((clusterId, currentCluster) -> {
                    validateClients(clusterId, currentCluster.getClients(), next.getContainerClusters().get(clusterId).getClients(), overrides, now);
                });

        return List.of();
    }

    void validateClients(String clusterId, List<Client> current, List<Client> next, ValidationOverrides overrides, Instant now) {

        List<X509Certificate> currentCertificates = current.stream()
                .map(Client::certificates)
                .flatMap(Collection::stream)
                .toList();
        List<X509Certificate> nextCertificates = next.stream()
                .map(Client::certificates)
                .flatMap(Collection::stream)
                .toList();

        List<X509Certificate> missingCerts = currentCertificates.stream().filter(cert -> !nextCertificates.contains(cert)).toList();
        if (!missingCerts.isEmpty()) {
            overrides.invalid(ValidationId.certificateRemoval,
                              "Data plane certificate(s) from cluster '" + clusterId + "' is removed " +
                              "(removed certificates: " + missingCerts.stream().map(x509Certificate -> x509Certificate.getSubjectX500Principal().getName()).toList() + ") " +
                              "This can cause client connection issues.",
                              now);
        }

    }
}
