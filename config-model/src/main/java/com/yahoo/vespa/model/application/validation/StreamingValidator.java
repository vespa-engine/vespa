// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.document.DataType;
import com.yahoo.document.NumericDataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.schema.document.MatchType;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.search.SearchCluster;
import com.yahoo.vespa.model.search.StreamingSearchCluster;

import java.util.List;
import java.util.logging.Level;

/**
 * Validates streaming mode
 */
public class StreamingValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        List<SearchCluster> searchClusters = model.getSearchClusters();
        for (SearchCluster cluster : searchClusters) {
            if ( ! cluster.isStreaming()) continue;

            var streamingCluster = (StreamingSearchCluster)cluster;
            warnStreamingAttributes(streamingCluster, deployState.getDeployLogger());
            warnStreamingGramMatching(streamingCluster, deployState.getDeployLogger());
            failStreamingDocumentReferences(streamingCluster);
        }
    }

    private static void warnStreamingGramMatching(StreamingSearchCluster sc, DeployLogger logger) {
        for (ImmutableSDField sd : sc.derived().getSchema().allConcreteFields()) {
            if (sd.getMatching().getType() == MatchType.GRAM) {
                logger.logApplicationPackage(Level.WARNING, "For streaming search cluster '" +
                                                            sc.getClusterName() +
                                                            "', SD field '" + sd.getName() +
                                                            "': n-gram matching is not supported for streaming search.");
            }
        }
    }

    /**
     * Warn if one or more attributes are defined in a streaming search cluster SD.
     *
     * @param sc     a search cluster to be checked for attributes in streaming search
     * @param logger a DeployLogger
     */
    private static void warnStreamingAttributes(StreamingSearchCluster sc, DeployLogger logger) {
        for (ImmutableSDField sd : sc.derived().getSchema().allConcreteFields()) {
            if (sd.doesAttributing()) {
                warnStreamingAttribute(sc, sd, logger);
            }
        }
    }

    private static void warnStreamingAttribute(StreamingSearchCluster sc, ImmutableSDField sd, DeployLogger logger) {
        // If the field is numeric, we can't print this, because we may have converted the field to
        // attribute indexing ourselves (IntegerIndex2Attribute)
        if (sd.getDataType() instanceof NumericDataType) return;
        // Tensor fields are only searchable via nearest neighbor search, and match semantics are irrelevant.
        if (sd.getDataType() instanceof TensorDataType) return;
        logger.logApplicationPackage(Level.WARNING, "For streaming search cluster '" + sc.getClusterName() +
                                                    "', SD field '" + sd.getName() +
                                                    "': 'attribute' has same match semantics as 'index'.");
    }

    private static void failStreamingDocumentReferences(StreamingSearchCluster sc) {
        for (Attribute attribute : sc.derived().getAttributeFields().attributes()) {
            DataType dataType = attribute.getDataType();
            if (dataType instanceof NewDocumentReferenceDataType) {
                String errorMessage = String.format("For streaming search cluster '%s': Attribute '%s' has type '%s'. " +
                                                    "Document references and imported fields are not allowed in streaming search.",
                                                    sc.getClusterName(), attribute.getName(), dataType.getName());
                throw new IllegalArgumentException(errorMessage);
            }
        }
    }

}
