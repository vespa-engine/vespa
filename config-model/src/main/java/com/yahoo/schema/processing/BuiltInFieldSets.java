// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.DocumentType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.document.Field;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Adds field sets for 1) fields defined inside document type 2) fields inside search but outside document
 *
 * @author Vegard Havdal
 */
public class BuiltInFieldSets extends Processor {

    public static final String SEARCH_FIELDSET_NAME = "[search]";     // Public due to oddities in position handling.
    public static final String INTERNAL_FIELDSET_NAME = "[internal]"; // This one populated from misc places

    public BuiltInFieldSets(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        addDocumentFieldSet();
        addSearchFieldSet();
        // "Hook" the field sets on search onto the document types, since we will include them
        // on the document configs
        schema.getDocument().setFieldSets(schema.fieldSets());
    }

    private void addSearchFieldSet() {
        for (SDField searchField : schema.extraFieldList()) {
            schema.fieldSets().addBuiltInFieldSetItem(SEARCH_FIELDSET_NAME, searchField.getName());
        }
    }

    private void addDocumentFieldSet() {
        for (Field docField : schema.getDocument().fieldSet()) {
            if (docField instanceof SDField && ((SDField) docField).isExtraField()) {
                continue; // skip
            }
            schema.fieldSets().addBuiltInFieldSetItem(DocumentType.DOCUMENT, docField.getName());
        }
    }

    
    
}
