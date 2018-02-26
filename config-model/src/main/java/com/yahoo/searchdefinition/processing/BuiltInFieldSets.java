// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.document.Field;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Adds field sets for 1) fields defined inside document type 2) fields inside search but outside document
 * @author Vegard Havdal
 */
public class BuiltInFieldSets extends Processor {

    private static final String DOC_FIELDSET_NAME = "[document]";
    public static final String SEARCH_FIELDSET_NAME = "[search]";     // Public due to oddities in position handling.
    public static final String INTERNAL_FIELDSET_NAME = "[internal]"; // This one populated from misc places

    public BuiltInFieldSets(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        addDocumentFieldSet();
        addSearchFieldSet();
        // "Hook" the field sets on search onto the document types, since we will include them
        // on the document configs
        search.getDocument().setFieldSets(search.fieldSets());
    }

    private void addSearchFieldSet() {
        for (SDField searchField : search.extraFieldList()) {
            search.fieldSets().addBuiltInFieldSetItem(SEARCH_FIELDSET_NAME, searchField.getName());
        }
    }

    private void addDocumentFieldSet() {
        for (Field docField : search.getDocument().fieldSet()) {
            search.fieldSets().addBuiltInFieldSetItem(DOC_FIELDSET_NAME, docField.getName());
        }
    }

    
    
}
