// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.DataType;
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
    public void process() {
        for (SDField field : search.allFieldsList()) {
            if (field.getDataType() == DataType.TENSOR) {
                warnUseOfTensorFieldAsAttribute(field);
                validateIndexingScripsForTensorField(field);
                validateAttributeSettingForTensorField(field);
            } else {
                validateDataTypeForField(field);
            }
        }
    }

    private void warnUseOfTensorFieldAsAttribute(SDField field) {
        if (field.doesAttributing()) {
            // TODO (geirst): Remove when no longer beta
            warn(search, field, "An attribute of type 'tensor' is currently beta, and re-feeding data between Vespa versions might be required.");
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

    private void validateDataTypeForField(SDField field) {
        if (field.getDataType().getPrimitiveType() == DataType.TENSOR) {
            fail(search, field, "A field with collection type of tensor is not supported. Use simple type 'tensor' instead.");
        }
    }
}
