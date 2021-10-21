// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.PositionDataType;
import com.yahoo.searchdefinition.Schema;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.ImmutableSDField;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * This processor creates a {@link com.yahoo.searchdefinition.document.SDDocumentType} for each {@link Schema}
 * object which holds all the data that search
 * associates with a document described in a search definition file. This includes all extra fields, summary fields and
 * implicit fields. All non-indexed and non-summary fields are discarded.
 */
public class AddExtraFieldsToDocument extends Processor {

    AddExtraFieldsToDocument(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    //TODO This is a tempoarry hack to avoid producing illegal code for fields not wanted anyway.
    private boolean dirtyLegalFieldNameCheck(String fieldName) {
        return ! fieldName.contains(".") && !"rankfeatures".equals(fieldName) && !"summaryfeatures".equals(fieldName);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        SDDocumentType document = schema.getDocument();
        if (document != null) {
            for (SDField field : schema.extraFieldList()) {
                addSdField(schema, document, field, validate);
            }
            //TODO Vespa 8 or sooner we should avoid the dirty addition of fields from dirty 'default' summary to document at all
            for (SummaryField field : schema.getSummary("default").getSummaryFields().values()) {
                if (dirtyLegalFieldNameCheck(field.getName())) {
                    addSummaryField(schema, document, field, validate);
                }
            }
        }
    }

    private void addSdField(Schema schema, SDDocumentType document, SDField field, boolean validate) {
        if (! field.hasIndex() && field.getAttributes().isEmpty()) {
            return;
        }
        for (Attribute atr : field.getAttributes().values()) {
            // TODO Vespa 8 or before: Check if this should be removed or changed to _zcurve.
            if (atr.getName().equals(field.getName() + "_position")) {
                DataType type = PositionDataType.INSTANCE;
                if (atr.getCollectionType().equals(Attribute.CollectionType.ARRAY)) {
                    type = DataType.getArray(type);
                }
                addField(schema, document, new SDField(document, atr.getName(), type), validate);
            } else if (!atr.getName().equals(field.getName())) {
                addField(schema, document, new SDField(document, atr.getName(), atr.getDataType()), validate);
            }
        }
        addField(schema, document, field, validate);
    }

    private void addSummaryField(Schema schema, SDDocumentType document, SummaryField field, boolean validate) {
        Field docField = document.getField(field.getName());
        if (docField == null) {
            ImmutableSDField existingField = schema.getField(field.getName());
            if (existingField == null) {
                SDField newField = new SDField(document, field.getName(), field.getDataType(), true);
                newField.setIsExtraField(true);
                document.addField(newField);
            } else if (!existingField.isImportedField()) {
                document.addField(existingField.asField());
            }
        } else if (!docField.getDataType().equals(field.getDataType())) {
            if (validate)
                throw newProcessException(schema, field, "Summary field has conflicting type.");
        }
    }

    private void addField(Schema schema, SDDocumentType document, Field field, boolean validate) {
        if (document.getField(field.getName()) != null && !(document.getField(field.getName()) == field)) {
            if (validate)
                throw newProcessException(schema, field, "Field shadows another.");
        }
        document.addField(field);
    }
}
