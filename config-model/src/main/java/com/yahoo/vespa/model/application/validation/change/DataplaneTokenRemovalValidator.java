// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.DataplaneToken;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.container.http.Client;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Check that data-plan token is not removed from a cluster without a validation override.
 * Based on {@link CertificateRemovalChangeValidator}.
 * @author hmusum
 */
public class DataplaneTokenRemovalValidator implements ChangeValidator {

    private static final Logger logger = Logger.getLogger(DataplaneTokenRemovalValidator.class.getName());

    @Override
    public void validate(ChangeContext context) {
        // Skip for tester applications
        if (context.previousModel().applicationPackage().getApplicationId().instance().isTester()) return;

        context.previousModel().getContainerClusters()
                .forEach((clusterId, currentCluster) -> {
                    if(context.model().getContainerClusters().containsKey(clusterId))
                        validateClients(clusterId,
                                        currentCluster.getClients(),
                                        context.model().getContainerClusters().get(clusterId).getClients(),
                                        context::invalid);
                });
    }

    void validateClients(String clusterId, List<Client> current, List<Client> next, BiConsumer<ValidationId, String> reporter) {
        List<DataplaneToken> currentTokens = current.stream()
                .filter(client -> !client.internal())
                .map(Client::tokens)
                .flatMap(Collection::stream)
                .toList();
        List<DataplaneToken> nextTokens = next.stream()
                                                    .filter(client -> !client.internal())
                                                    .map(Client::tokens)
                                                    .flatMap(Collection::stream)
                                                    .toList();

        logger.log(Level.FINE, "Tokens for cluster %s: Current: [%s], Next: [%s]"
                .formatted(clusterId,
                           currentTokens.stream().map(DataplaneToken::tokenId).collect(Collectors.joining(", ")),
                           nextTokens.stream().map(DataplaneToken::tokenId).collect(Collectors.joining(", "))));

        List<DataplaneToken> missingTokens = currentTokens.stream().filter(token -> !nextTokens.contains(token)).toList();
        if (!missingTokens.isEmpty()) {
            reporter.accept(ValidationId.dataPlaneTokenEndpointRemoval,
                            "Data plane token(s) from cluster '" + clusterId + "' is removed " +
                            "(removed tokens: " + missingTokens.stream().map(DataplaneToken::tokenId).toList() + ") " +
                            "This can cause client connection issues.");
        }
    }

}
