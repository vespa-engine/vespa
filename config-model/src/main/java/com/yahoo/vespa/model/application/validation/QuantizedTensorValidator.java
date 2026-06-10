// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.SchemaInfo;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.text.Text;
import com.yahoo.vespa.model.search.SearchCluster;

/**
 * Validator for ensuring quantization is only used on compatible tensor types.
 */
public class QuantizedTensorValidator  implements Validator {

    @Override
    public void validate(Validation.Context context) {
        forEachAttribute(context, (schema, field, attr) -> {
            if (!attr.isQuantized()) {
                return;
            }
            if (attr.tensorType().isEmpty()) {
                context.illegal(makeFieldMessage(schema, field, "quantization can only be set on tensor fields"));
                return;
            }
            if (!attr.tensorType().get().hasIndexedDimensions()) {
                context.illegal(makeFieldMessage(schema, field, "quantization is not supported for sparse tensors (only mixed and dense)"));
            }
            // Geodegrees has only 2 dimensions; way too few to work well with quantization
            if (attr.distanceMetric().equals(Attribute.DistanceMetric.GEODEGREES)) {
                context.illegal(makeFieldMessage(schema, field, "distance metric 'geodegrees' is not supported for quantized tensors"));
            }
            // Hamming is already binarized; makes no sense to use with quantization
            if (attr.distanceMetric().equals(Attribute.DistanceMetric.HAMMING)) {
                context.illegal(makeFieldMessage(schema, field, "distance metric 'hamming' is not supported for quantized tensors"));
            }
        });
    }

    private static String makeFieldMessage(Schema schema, ImmutableSDField field, String msg) {
        return Text.format("For %s, field '%s': %s", schema, field.getName(), msg);
    }

    @FunctionalInterface
    interface FieldAttrConsumer {
        void invoke(Schema schema, ImmutableSDField field, Attribute attr);
    }

    private static void forEachAttribute(Validation.Context context, FieldAttrConsumer consumer) {
        for (SearchCluster cluster : context.model().getSearchClusters()) {
            for (SchemaInfo schemaInfo : cluster.schemas().values()) {
                Schema schema = schemaInfo.fullSchema();
                for (ImmutableSDField field : schema.allConcreteFields()) {
                    for (var nameAndAttr : field.getAttributes().entrySet()) {
                        Attribute attr = nameAndAttr.getValue();
                        consumer.invoke(schema, field, attr);
                    }
                }
            }
        }
    }

}
