// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.processing.multifieldresolver.IndexCommandResolver;
import com.yahoo.schema.processing.multifieldresolver.RankTypeResolver;
import com.yahoo.schema.processing.multifieldresolver.StemmingResolver;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.List;
import java.util.Map;

/**
 * Ensures that there are no conflicting types or field settings
 * in multifield indices, either by changing settings or by splitting
 * conflicting fields in multiple ones with different settings.
 *
 * @author bratseth
 */
public class MultifieldIndexHarmonizer extends Processor {

    /** A map from index names to a List of fields going to that index */
    private Map<String,List<SDField>> indexToFields=new java.util.HashMap<>();

    public MultifieldIndexHarmonizer(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        populateIndexToFields(schema);
        resolveAllConflicts(schema);
    }

    private void populateIndexToFields(Schema schema) {
        for (SDField field : schema.allConcreteFields() ) {
            if ( ! field.doesIndexing()) continue;
            addIndexField(field.getName(), field);
        }
    }

    private void addIndexField(String indexName,SDField field) {
        List<SDField> fields = indexToFields.get(indexName);
        if (fields == null) {
            fields = new java.util.ArrayList<>();
            indexToFields.put(indexName, fields);
        }
        fields.add(field);
    }

    private void resolveAllConflicts(Schema schema) {
        for (Map.Entry<String, List<SDField>> entry : indexToFields.entrySet()) {
            String indexName = entry.getKey();
            List<SDField> fields = entry.getValue();
            if (fields.size() == 1) continue; // It takes two to make a conflict
            resolveConflicts(indexName, fields, schema);
        }
    }

    /**
     * Resolves all conflicts for one index
     *
     * @param indexName the name of the index in question
     * @param fields all the fields indexed to this index
     * @param schema the search definition having this
     */
    private void resolveConflicts(String indexName, List<SDField> fields, Schema schema) {
        new StemmingResolver(indexName, fields, schema, deployLogger).resolve();
        new IndexCommandResolver(indexName, fields, schema, deployLogger).resolve();
        new RankTypeResolver(indexName, fields, schema, deployLogger).resolve();
    }

}
