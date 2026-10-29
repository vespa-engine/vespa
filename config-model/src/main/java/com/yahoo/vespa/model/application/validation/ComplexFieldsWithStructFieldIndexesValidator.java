// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.SchemaInfo;
import com.yahoo.schema.document.ComplexIndexFieldUtils;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.text.Text;
import com.yahoo.vespa.model.application.validation.Validation.Context;

import java.util.stream.Collectors;

import static java.util.logging.Level.WARNING;

/**
 * Validates that complex fields (of type struct or map) do not have any unsupported struct fields with 'indexing: index'.
 * Supported struct field indexes are listed in {@link ComplexIndexFieldUtils}; other struct field indexes
 * are not supported and will confuse the user if not validated.
 *
 * Only applies for indexed search clusters.
 *
 * @author Geir Storli
 */
public class ComplexFieldsWithStructFieldIndexesValidator implements Validator {

    @Override
    public void validate(Context context) {
        for (var cluster : context.model().getSearchClusters()) {
            for (var spec : cluster.schemas().values()) {
                if (spec.getIndexMode() == SchemaInfo.IndexMode.INDEX) {
                    validateComplexFields(cluster.getClusterName(), spec.fullSchema(), context);
                }
            }
        }
    }

    private static void validateComplexFields(String clusterName, Schema schema, Context context) {
        String unsupportedFields = schema.allFields()
                .filter(field -> ! ComplexIndexFieldUtils.unsupportedIndexedStructFields(field).isEmpty())
                .map(ComplexFieldsWithStructFieldIndexesValidator::toString)
                .collect(Collectors.joining(", "));

        if (unsupportedFields.isEmpty()) return;

        if (context.deployState().featureFlags().failWhenConfiguringIndexedMapOfArray()) {
            throw new IllegalArgumentException(Text.format(
                    "For cluster '%s', schema '%s': The following complex fields have struct fields with 'indexing: index' " +
                            "which is not supported: %s. " +
                            "Remove setting or change to 'indexing: attribute' if needed for matching.",
                    clusterName, schema.getName(), unsupportedFields));

        } else {
            context.deployState().getDeployLogger().logApplicationPackage(
                    WARNING,
                    Text.format("For cluster '%s', schema '%s': The following complex fields have struct fields with 'indexing: index' which is not supported and has no effect: %s. " +
                                        "Remove setting or change to 'indexing: attribute' if needed for matching.",
                                clusterName, schema.getName(), unsupportedFields));
        }


    }

    private static String toString(ImmutableSDField field) {
        return field.getName() + " (" + ComplexIndexFieldUtils.unsupportedIndexedStructFields(field).stream()
                                                              .map(structField -> structField.getName() + ": " +
                                                                                  ComplexIndexFieldUtils.unsupportedReason(field, structField).orElseThrow())
                                                              .collect(Collectors.joining("; ")) + ")";
    }

}
