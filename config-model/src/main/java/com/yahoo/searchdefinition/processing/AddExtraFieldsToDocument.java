// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.PositionDataType;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.ImmutableSDField;
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
    public void process(boolean validate) {
        SDDocumentType document = search.getDocument();
        if (document != null) {
            for (Field field : search.extraFieldList()) {
                addSdField(search, document, (SDField)field, validate);
            }
            for (SummaryField field : search.getSummary("default").getSummaryFields()) {
                addSummaryField(search, document, field, validate);
            }
        }
    }

    private void addSdField(Search search, SDDocumentType document, SDField field, boolean validate) {
        if (field.getIndexToCount() == 0 && field.getAttributes().isEmpty()) {
            return;
        }
        for (Attribute atr : field.getAttributes().values()) {
            if (atr.getName().equals(field.getName() + "_position")) {
                DataType type = PositionDataType.INSTANCE;
                if (atr.getCollectionType().equals(Attribute.CollectionType.ARRAY)) {
                    type = DataType.getArray(type);
                }
                addField(search, document, new SDField(document, atr.getName(), type), validate);
            } else if (!atr.getName().equals(field.getName())) {
                addField(search, document, new SDField(document, atr.getName(), atr.getDataType()), validate);
            }
        }
        addField(search, document, field, validate);
    }

    private void addSummaryField(Search search, SDDocumentType document, SummaryField field, boolean validate) {
        Field docField = document.getField(field.getName());
        if (docField == null) {
            ImmutableSDField existingField = search.getField(field.getName());
            if (existingField == null) {
                SDField newField = new SDField(document, field.getName(), field.getDataType(), field.isHeader(), true);
                newField.setIsExtraField(true);
                document.addField(newField);
            } else if (!existingField.isImportedField()) {
                document.addField(existingField.asField());
            }
        } else if (!docField.getDataType().equals(field.getDataType())) {
            if (validate)
                throw newProcessException(search, field, "Summary field has conflicting type.");
        }
    }

    private void addField(Search search, SDDocumentType document, Field field, boolean validate) {
        if (document.getField(field.getName()) != null) {
            if (validate)
                throw newProcessException(search, field, "Field shadows another.");
        }
        document.addField(field);
    }
}
