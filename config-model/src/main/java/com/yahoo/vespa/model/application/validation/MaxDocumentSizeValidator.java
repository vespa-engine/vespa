// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.vespa.model.application.validation.Validation.Context;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import java.util.OptionalInt;
import java.util.logging.Level;

/**
 * Validates that the {@code <max-document-size>} configured under {@code <document-api>} is not
 * larger than the smallest {@code max-document-size} configured on any content cluster — otherwise
 * the document API would accept requests the content cluster will reject.
 *
 * <p>Currently issues a deploy WARNING. TODO: tighten to a hard failure once usage is understood.
 *
 * @author hmusum
 */
public class MaxDocumentSizeValidator implements Validator {

    @Override
    public void validate(Context context) {
        OptionalInt smallestContentMax = smallestContentClusterMaxDocumentSizeMib(context);
        if (smallestContentMax.isEmpty()) return;

        for (var entry : context.model().getContainerClusters().entrySet()) {
            String clusterId = entry.getKey();
            ApplicationContainerCluster cluster = entry.getValue();
            int containerMax = cluster.getMaxDocumentOperationRequestSizeMib();
            if (containerMax > smallestContentMax.getAsInt()) {
                context.deployState().getDeployLogger().log(Level.WARNING,
                        "max-document-size in <document-api> for container cluster '" + clusterId +
                        "' is " + containerMax + " MiB, which is larger than the smallest content cluster " +
                        "max-document-size of " + smallestContentMax.getAsInt() + " MiB. " +
                        "Documents larger than the content cluster limit will be rejected. " +
                        "This will become a hard failure in a future release.");
            }
        }
    }

    private static OptionalInt smallestContentClusterMaxDocumentSizeMib(Context context) {
        return context.model().getContentClusters().values().stream()
                .map(ContentCluster::getDistributorNodes)
                .filter(d -> d != null)
                .mapToInt(d -> d.getMaxDocumentOperationSizeMib())
                .filter(v -> v > 0)
                .min();
    }
}
