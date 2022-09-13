// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.document.Field;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * This processor creates a {@link com.yahoo.schema.document.SDDocumentType} for each {@link Schema}
 * object which holds all the data that search
 * associates with a document described in a search definition file. This includes all extra fields, summary fields and
 * implicit fields. All non-indexed and non-summary fields are discarded.
 */
public class AddExtraFieldsToDocument extends Processor {

    AddExtraFieldsToDocument(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        SDDocumentType document = schema.getDocument();
        if (document != null) {
            for (SDField field : schema.extraFieldList()) {
                addSdField(schema, document, field, validate);
            }
            for (var docsum : schema.getSummaries().values()) {
                for (var summaryField : docsum.getSummaryFields().values()) {
                    var transform = summaryField.getTransform();
                    if (transform.isDynamic() && DynamicSummaryTransformUtils.summaryFieldIsRequiredInDocumentType(summaryField) ||
                            transform == SummaryTransform.NONE ||
                            transform == SummaryTransform.DOCUMENT_ID)
                    {
                        // TODO: Adding the 'documentid' field should no longer be needed when the docsum framework in the backend has been simplified and the transform is always used.
                        addSummaryField(schema, document, summaryField, validate);
                    } else {
                        // skip: generated from attribute or similar,
                        // so does not need to be included as an extra
                        // field in the document type
                    }
                }
            }
        }
    }

    private void addSdField(Schema schema, SDDocumentType document, SDField field, boolean validate) {
        if (! field.hasIndex() && field.getAttributes().isEmpty()) {
            return;
        }
        for (Attribute atr : field.getAttributes().values()) {
            if (!atr.getName().equals(field.getName())) {
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
                SDField newField = new SDField(document, field.getName(), field.getDataType());
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
