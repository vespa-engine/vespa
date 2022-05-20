// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.HashMap;
import java.util.Map;

/**
 * This Processor makes sure all fields with the same name have the same {@link DataType}. This check
 * explicitly disregards whether a field is an index field, an attribute or a summary field. This is a requirement if we
 * hope to move to a model where index fields, attributes and summary fields share a common field class.
 *
 * @author Simon Thoresen Hult
 */
public class ValidateFieldTypes extends Processor {

    public ValidateFieldTypes(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if (!validate) return;

        String searchName = schema.getName();
        Map<String, DataType> seenFields = new HashMap<>();
        verifySearchAndDocFields(searchName, seenFields);
        verifySummaryFields(searchName, seenFields);
    }

    final protected void verifySearchAndDocFields(String searchName, Map<String, DataType> seenFields) {
        schema.allFields().forEach(field -> {
            checkFieldType(searchName, "index field", field.getName(), field.getDataType(), seenFields);
            for (Map.Entry<String, Attribute> entry : field.getAttributes().entrySet()) {
                checkFieldType(searchName, "attribute", entry.getKey(), entry.getValue().getDataType(), seenFields);
            }
        });

    }
    final protected void verifySummaryFields(String searchName, Map<String, DataType> seenFields) {
        for (DocumentSummary summary : schema.getSummaries().values()) {
            for (SummaryField field : summary.getSummaryFields().values()) {
                checkFieldType(searchName, "summary field", field.getName(), field.getDataType(), seenFields);
            }
        }
    }

    private void checkFieldType(String searchName, String fieldDesc, String fieldName, DataType fieldType,
                                Map<String, DataType> seenFields) {
        DataType seenType = seenFields.get(fieldName);
        if (seenType == null) {
            seenFields.put(fieldName, fieldType);
        } else if ( ! compatibleTypes(seenType, fieldType)) {
            throw newProcessException(searchName, fieldName, "Incompatible types. Expected " +
                                                             seenType.getName() + " for " + fieldDesc +
                                                             " '" + fieldName + "', got " + fieldType.getName() + ".");
        }
    }

    private static boolean compatibleTypes(DataType seenType, DataType fieldType) {
        // legacy tag field type compatibility; probably not needed any more (Oct 2016)
        if ("tag".equals(seenType.getName())) {
            return "tag".equals(fieldType.getName()) || "WeightedSet<string>".equals(fieldType.getName());
        }
        if ("tag".equals(fieldType.getName())) {
            return "tag".equals(seenType.getName()) || "WeightedSet<string>".equals(seenType.getName());
        }
        if (seenType instanceof TensorDataType && fieldType instanceof TensorDataType) {
            return fieldType.isAssignableFrom(seenType); // TODO: Just do this for all types
        }
        return seenType.equals(fieldType);
    }

}
