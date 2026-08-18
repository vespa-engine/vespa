// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.document.DataType;
import com.yahoo.document.MapDataType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Adds a "fieldName.$keyvalue" attribute to maps with fast-search enabled.
 *
 * @author johsol
 */
public class CreateFastMapSearch extends Processor {

    private final SDDocumentType repo;

    public CreateFastMapSearch(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
        repo = schema.getDocument();
    }

    @Override
    public void process(boolean validate, boolean documentsOnly, ModelContext.Properties properties) {
        process(validate, documentsOnly);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (SDField field : schema.allConcreteFields()) {
            if (!shouldCreateFastMapAttribute(field)) {
                continue;
            }

            String fieldName = field.getName();

            SDField fastMapField = createFastMapField(field, fieldName + ".$keyvalue", validate);
            schema.addExtraField(fastMapField);
        }
    }

    /**
     * Returns whether a fast map attribute should be created for the given field.
     */
    private boolean shouldCreateFastMapAttribute(SDField field) {
        DataType dataType = field.getDataType();
        return (dataType instanceof MapDataType)
                && field.doesAttributing()
                && field.hasFastMapSearch();
    }

    /**
     * Creates a synthetic attribute for a map with fast search. The attribute has data
     * type array of strings since we will do lexical search on the key-value pairs of the map.
     */
    private SDField createFastMapField(SDField inputField, String fieldName, boolean validate) {
        if (validate && schema.getConcreteField(fieldName) != null || schema.getAttribute(fieldName) != null) {
            throw newProcessException(schema, null, "Incompatible map attribute '" + fieldName + "' already created.");
        }

        SDField field = new SDField(repo, fieldName, DataType.getArray(DataType.STRING));
        Attribute attribute = new Attribute(fieldName, Attribute.Type.STRING, Attribute.CollectionType.ARRAY);
        attribute.setFastSearch(true);
        field.addAttribute(attribute);

        return field;
    }

}
