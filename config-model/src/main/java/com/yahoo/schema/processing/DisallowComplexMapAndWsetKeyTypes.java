// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.Field;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.document.DataType;
import com.yahoo.document.MapDataType;
import com.yahoo.document.PrimitiveDataType;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.SDField;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Non-primitive key types for map and weighted set forbidden (though OK in document model)
 *
 * @author Vegard Havdal
 */
public class DisallowComplexMapAndWsetKeyTypes extends Processor {

    public DisallowComplexMapAndWsetKeyTypes(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if ( ! validate) return;

    	// TODO also traverse struct types to search for bad map or wset types.
        // Do this after document manager is fixed, do not start using the static stuff on SDDocumentTypes any more.
        for (SDField field : schema.allConcreteFields()) {
            checkFieldType(field, field.getDataType());
        }
    }

    private void checkFieldType(Field field, DataType dataType) {
        if (dataType instanceof ArrayDataType) {
            DataType nestedType = ((ArrayDataType) dataType).getNestedType();
            checkFieldType(field, nestedType);
        } else if (dataType instanceof WeightedSetDataType) {
            DataType nestedType = ((WeightedSetDataType) dataType).getNestedType();
            if ( ! (nestedType instanceof PrimitiveDataType)) {
                fail(schema, field, "Weighted set must have a primitive key type.");
            }
        } else if (dataType instanceof MapDataType) {
            DataType keyType = ((MapDataType) dataType).getKeyType();
            if ( ! (keyType instanceof PrimitiveDataType)) {
                fail(schema, field, "Map key type must be a primitive type.");
            }
            checkFieldType(field, ((MapDataType) dataType).getValueType());
        }

    }

}
