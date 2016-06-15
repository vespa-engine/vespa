// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.document.DataType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.HashMap;
import java.util.Map;

/**
 * This Processor checks to make sure all fields with the same name have the same {@link DataType}. This check
 * explicitly disregards whether a field is an index field, an attribute or a summary field. This is a requirement if we
 * hope to move to a model where index fields, attributes and summary fields share a common field class.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class ValidateFieldTypes extends Processor {

    public ValidateFieldTypes(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process() {
        String searchName = search.getName();
        Map<String, DataType> fieldTypes = new HashMap<>();
        for (SDField field : search.allFieldsList()) {
            checkFieldType(searchName, "index field", field.getName(), field.getDataType(), fieldTypes);
            for (Map.Entry<String, Attribute> entry : field.getAttributes().entrySet()) {
                checkFieldType(searchName, "attribute", entry.getKey(), entry.getValue().getDataType(), fieldTypes);
            }
        }
        for (DocumentSummary summary : search.getSummaries().values()) {
            for (SummaryField field : summary.getSummaryFields()) {
                checkFieldType(searchName, "summary field", field.getName(), field.getDataType(), fieldTypes);
            }
        }
    }

    private void checkFieldType(String searchName, String fieldDesc, String fieldName, DataType fieldType,
                                Map<String, DataType> fieldTypes)
    {
        DataType prevType = fieldTypes.get(fieldName);
        if (prevType == null) {
            fieldTypes.put(fieldName, fieldType);
        } else if (!equalTypes(prevType, fieldType)) {
            throw newProcessException(searchName, fieldName, "Duplicate field name with different types. Expected " + prevType.getName() + " for " + fieldDesc +
                                                             " '" + fieldName + "', got " + fieldType.getName() + ".");
        }
    }
    
    private boolean equalTypes(DataType d1, DataType d2) {
        if ("tag".equals(d1.getName())) {
            return "tag".equals(d2.getName()) || "WeightedSet<string>".equals(d2.getName());
        }
        if ("tag".equals(d2.getName())) {
            return "tag".equals(d1.getName()) || "WeightedSet<string>".equals(d1.getName());
        }
        return d1.equals(d2);
    }
    
}
