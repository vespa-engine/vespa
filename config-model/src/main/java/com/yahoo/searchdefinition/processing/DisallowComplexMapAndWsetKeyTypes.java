// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.document.DataType;
import com.yahoo.document.MapDataType;
import com.yahoo.document.PrimitiveDataType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Non-primitive key types for map and weighted set forbidden (though OK in document model)
 *
 * @author Vegard Havdal
 */
public class DisallowComplexMapAndWsetKeyTypes extends Processor {

    public DisallowComplexMapAndWsetKeyTypes(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;

    	// TODO also traverse struct types to search for bad map or wset types there. Do this after document manager is fixed, do
    	// not start using the static stuff on SDDocumentTypes any more.
        for (SDField field : search.allConcreteFields()) {
            if (field.getDataType() instanceof WeightedSetDataType) {
                DataType nestedType = ((WeightedSetDataType)field.getDataType()).getNestedType();
                if ( ! (nestedType instanceof PrimitiveDataType)) {
                    fail(search, field, "Weighted set must have a primitive key type.");
                }
            } else if (field.getDataType() instanceof MapDataType) {
                if ( ! (((MapDataType)field.getDataType()).getKeyType() instanceof PrimitiveDataType)) {
                    fail(search, field, "Map key type must be a primitive type");
                }
            }
        }
    }

}
