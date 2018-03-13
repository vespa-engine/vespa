// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.CollectionDataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Class that processes and validates tensor fields.
 *
 * @author geirst
 */
public class TensorFieldProcessor extends Processor {

    public TensorFieldProcessor(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;

        for (SDField field : search.allConcreteFields()) {
            if ( field.getDataType() instanceof TensorDataType ) {
                validateIndexingScripsForTensorField(field);
                validateAttributeSettingForTensorField(field);
            }
            else if (field.getDataType() instanceof CollectionDataType){
                validateDataTypeForCollectionField(field);
            }
        }
    }

    private void validateIndexingScripsForTensorField(SDField field) {
        if (field.doesIndexing()) {
            fail(search, field, "A field of type 'tensor' cannot be specified as an 'index' field.");
        }
    }

    private void validateAttributeSettingForTensorField(SDField field) {
        if (field.doesAttributing()) {
            Attribute attribute = field.getAttributes().get(field.getName());
            if (attribute != null && attribute.isFastSearch()) {
                fail(search, field, "An attribute of type 'tensor' cannot be 'fast-search'.");
            }
        }
    }

    private void validateDataTypeForCollectionField(SDField field) {
        if (((CollectionDataType)field.getDataType()).getNestedType() instanceof TensorDataType)
            fail(search, field, "A field with collection type of tensor is not supported. Use simple type 'tensor' instead.");
    }

}
