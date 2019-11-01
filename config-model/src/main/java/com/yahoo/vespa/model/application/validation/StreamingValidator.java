// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.document.DataType;
import com.yahoo.document.NumericDataType;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.ImmutableSDField;
import com.yahoo.searchdefinition.document.Matching;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.search.AbstractSearchCluster;
import com.yahoo.vespa.model.search.SearchCluster;

import java.util.List;
import java.util.logging.Level;


/**
 * Validates streaming mode
 */
public class StreamingValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        List<AbstractSearchCluster> searchClusters = model.getSearchClusters();
        for (AbstractSearchCluster cluster : searchClusters) {
            if (!cluster.isStreaming()) {
                continue;
            }
            SearchCluster sc = (SearchCluster) cluster;
            warnStreamingAttributes(sc, deployState.getDeployLogger());
            warnStreamingGramMatching(sc, deployState.getDeployLogger());
            failStreamingDocumentReferences(sc);
        }
    }

    private static void warnStreamingGramMatching(SearchCluster sc, DeployLogger logger) {
        if (sc.getSdConfig() != null) {
            for (ImmutableSDField sd : sc.getSdConfig().getSearch().allConcreteFields()) {
                if (sd.getMatching().getType().equals(Matching.Type.GRAM)) {
                    logger.log(Level.WARNING, "For streaming search cluster '" + sc.getClusterName() +
                            "', SD field '" + sd.getName() + "': n-gram matching is not supported for streaming search.");
                }
            }
        }
    }

    /**
     * Warn if one or more attributes are defined in a streaming search cluster SD.
     *
     * @param sc     a search cluster to be checked for attributes in streaming search
     * @param logger a DeployLogger
     */
    private static void warnStreamingAttributes(SearchCluster sc, DeployLogger logger) {
        if (sc.getSdConfig() != null) {
            for (ImmutableSDField sd : sc.getSdConfig().getSearch().allConcreteFields()) {
                if (sd.doesAttributing()) {
                    warnStreamingAttribute(sc, sd, logger);
                }
            }
        }
    }

    private static void warnStreamingAttribute(SearchCluster sc, ImmutableSDField sd, DeployLogger logger) {
        // If the field is numeric, we can't print this, because we may have converted the field to
        // attribute indexing ourselves (IntegerIndex2Attribute)
        if (sd.getDataType() instanceof NumericDataType) return;
        logger.log(Level.WARNING, "For streaming search cluster '" + sc.getClusterName() +
                "', SD field '" + sd.getName() + "': 'attribute' has same match semantics as 'index'.");
    }

    private static void failStreamingDocumentReferences(SearchCluster sc) {
        for (Attribute attribute : sc.getSdConfig().getAttributeFields().attributes()) {
            DataType dataType = attribute.getDataType();
            if (dataType instanceof ReferenceDataType) {
                String errorMessage = String.format(
                        "For streaming search cluster '%s': Attribute '%s' has type '%s'. " +
                                "Document references and imported fields are not allowed in streaming search.",
                        sc.getClusterName(), attribute.getName(), dataType.getName());
                throw new IllegalArgumentException(errorMessage);
            }
        }
    }
}
