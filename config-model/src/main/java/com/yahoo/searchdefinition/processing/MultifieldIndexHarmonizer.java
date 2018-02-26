// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.processing.multifieldresolver.IndexCommandResolver;
import com.yahoo.searchdefinition.processing.multifieldresolver.RankTypeResolver;
import com.yahoo.searchdefinition.processing.multifieldresolver.StemmingResolver;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.Iterator;
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

    public MultifieldIndexHarmonizer(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        populateIndexToFields(search);
        resolveAllConflicts(search);
    }

    private void populateIndexToFields(Search search) {
        for (SDField field : search.allConcreteFields() ) {
            if ( ! field.doesIndexing()) continue;

            for (Iterator j = field.getFieldNameAsIterator(); j.hasNext();) {
                String indexName = (String)j.next();
                addIndexField(indexName, field);
            }
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

    private void resolveAllConflicts(Search search) {
        for (Map.Entry<String, List<SDField>> entry : indexToFields.entrySet()) {
            String indexName = entry.getKey();
            List<SDField> fields = entry.getValue();
            if (fields.size() == 1) continue; // It takes two to make a conflict
            resolveConflicts(indexName, fields, search);
        }
    }

    /**
     * Resolves all conflicts for one index
     *
     * @param indexName the name of the index in question
     * @param fields all the fields indexed to this index
     * @param search the search definition having this
     */
    private void resolveConflicts(String indexName,List<SDField> fields,Search search) {
        new StemmingResolver(indexName, fields, search, deployLogger).resolve();
        new IndexCommandResolver(indexName, fields, search, deployLogger).resolve();
        new RankTypeResolver(indexName, fields, search, deployLogger).resolve();
    }

}
