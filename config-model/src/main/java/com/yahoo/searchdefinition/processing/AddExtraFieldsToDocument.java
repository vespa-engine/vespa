// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.PositionDataType;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * This processor creates a {@link com.yahoo.searchdefinition.document.SDDocumentType} for each {@link Search} object which holds all the data that search
 * associates with a document described in a search definition file. This includes all extra fields, summary fields and
 * implicit fields. All non-indexed and non-summary fields are discarded.
 */
public class AddExtraFieldsToDocument extends Processor {

    public AddExtraFieldsToDocument(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process() {
        SDDocumentType document = search.getDocument();
        if (document != null) {
            for (Field field : search.extraFieldList()) {
                addSdField(search, document, (SDField)field);
            }
            for (SummaryField field : search.getSummary("default").getSummaryFields()) {
                addSummaryField(search, document, field);
            }
        }
    }

    private void addSdField(Search search, SDDocumentType document, SDField field) {
        if (field.getIndexToCount() == 0 && field.getAttributes().isEmpty()) {
            return;
        }
        for (Attribute atr : field.getAttributes().values()) {
            if (atr.getName().equals(field.getName() + "_position")) {
                DataType type = PositionDataType.INSTANCE;
                if (atr.getCollectionType().equals(Attribute.CollectionType.ARRAY)) {
                    type = DataType.getArray(type);
                }
                addField(search, document, new SDField(document, atr.getName(), type));
            } else if (!atr.getName().equals(field.getName())) {
                addField(search, document, new SDField(document, atr.getName(), atr.getDataType()));
            }
        }
        addField(search, document, field);
    }

    private void addSummaryField(Search search, SDDocumentType document, SummaryField field) {
        Field docField = document.getField(field.getName());
        if (docField == null) {
            SDField newField = search.getConcreteField(field.getName());
            if (newField == null) {
                newField = new SDField(document, field.getName(), field.getDataType(), field.isHeader(), true);
                newField.setIsExtraField(true);
            }
            document.addField(newField);
        } else if (!docField.getDataType().equals(field.getDataType())) {
            throw newProcessException(search, field, "Summary field has conflicting type.");
        }
    }

    private void addField(Search search, SDDocumentType document, Field field) {
        if (document.getField(field.getName()) != null) {
            throw newProcessException(search, field, "Field shadows another.");
        }
        document.addField(field);
    }
}
