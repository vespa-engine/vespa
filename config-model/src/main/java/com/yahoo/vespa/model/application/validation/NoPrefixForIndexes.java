// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.schema.document.MatchAlgorithm;
import com.yahoo.schema.Index;
import com.yahoo.schema.derived.DerivedConfiguration;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.search.SearchCluster;
import com.yahoo.vespa.model.search.DocumentDatabase;
import com.yahoo.vespa.model.search.IndexedSearchCluster;

import java.util.Map;

/**
 * match:prefix for indexed fields not supported
 *
 * @author vegardh
 */
public class NoPrefixForIndexes extends Validator {

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        for (SearchCluster cluster : model.getSearchClusters()) {
            if (cluster instanceof IndexedSearchCluster) {
                IndexedSearchCluster sc = (IndexedSearchCluster) cluster;
                for (DocumentDatabase docDb : sc.getDocumentDbs()) {
                    DerivedConfiguration sdConfig = docDb.getDerivedConfiguration();
                    Schema schema = sdConfig.getSchema();
                    for (ImmutableSDField field : schema.allConcreteFields()) {
                        if (field.doesIndexing()) {
                            //if (!field.getIndexTo().isEmpty() && !field.getIndexTo().contains(field.getName())) continue;
                            if (field.getMatching().getAlgorithm().equals(MatchAlgorithm.PREFIX)) {
                                failField(schema, field);
                            }
                            for (Map.Entry<String, Index> e : field.getIndices().entrySet()) {
                                if (e.getValue().isPrefix()) {
                                    failField(schema, field);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void failField(Schema schema, ImmutableSDField field) {
        throw new IllegalArgumentException("For " + schema + ", field '" + field.getName() +
                                           "': match/index:prefix is not supported for indexes.");
    }
}
