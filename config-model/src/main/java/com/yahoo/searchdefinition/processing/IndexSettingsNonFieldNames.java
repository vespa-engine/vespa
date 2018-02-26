// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.Index;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.Iterator;

/**
 * Fail if:
 * 1) There are index: settings without explicit index names (name same as field name)
 * 2) All the index-to indexes differ from the field name.
 *
 * @author Vegard Havdal
 */
public class IndexSettingsNonFieldNames extends Processor {

    public IndexSettingsNonFieldNames(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;

        for (SDField field : search.allConcreteFields()) {
            boolean fieldNameUsed = false;
            for (Iterator i = field.getFieldNameAsIterator(); i.hasNext();) {
                String iName = (String)(i.next());
                if (iName.equals(field.getName())) {
                    fieldNameUsed = true;
                }
            }
            if ( ! fieldNameUsed) {
                for (Index index : field.getIndices().values()) {
                    if (index.getName().equals(field.getName())) {
                        throw new IllegalArgumentException("Error in " + field + " in " + search +
                                                           ": When all index names differ from field name, index " +
                                                           "parameter settings must specify index name explicitly.");
                    }
                }
            }
        }
    }

}
