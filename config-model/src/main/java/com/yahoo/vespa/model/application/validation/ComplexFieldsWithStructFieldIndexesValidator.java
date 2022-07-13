// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.vespa.model.VespaModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Validates that complex fields (of type struct or map) do not have any struct fields with 'indexing: index'.
 * This is not supported and will confuse the user if not validated.
 *
 * Only applies for indexed search clusters.
 *
 * @author geirst
 */
public class ComplexFieldsWithStructFieldIndexesValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        for (var cluster : model.getSearchClusters()) {
            if (cluster.isStreaming()) {
                continue;
            }
            for (var spec : cluster.schemas().values()) {
                validateComplexFields(cluster.getClusterName(), spec.fullSchema(), deployState.getDeployLogger());
            }
        }
    }

    private static void validateComplexFields(String clusterName, Schema schema, DeployLogger logger) {
        String unsupportedFields = schema.allFields()
                .filter(field -> hasStructFieldsWithIndex(field))
                .map(ComplexFieldsWithStructFieldIndexesValidator::toString)
                .collect(Collectors.joining(", "));

        if (!unsupportedFields.isEmpty()) {
            // TODO (Vespa 9 or before): Change back to an exception when no applications are using it wrong.
            logger.logApplicationPackage(Level.WARNING,
                    String.format("For cluster '%s', schema '%s': The following complex fields have struct fields with 'indexing: index' which is not supported and has no effect: %s. " +
                                  "Remove setting or change to 'indexing: attribute' if needed for matching.",
                                  clusterName, schema.getName(), unsupportedFields));
        }
    }

    private static boolean hasStructFieldsWithIndex(ImmutableSDField field) {
        return (!field.isImportedField() && field.usesStructOrMap() && hasStructFieldsWithIndex(field.getStructFields()));
    }

    private static String toString(ImmutableSDField field) {
        return field.getName() + " (" + String.join(", ", getStructFieldsWithIndex(field.getStructFields())) + ")";
    }

    private static boolean hasStructFieldsWithIndex(Collection<? extends ImmutableSDField> structFields) {
        return !getStructFieldsWithIndex(structFields).isEmpty();
    }

    private static List<String> getStructFieldsWithIndex(Collection<? extends ImmutableSDField> structFields) {
        var result = new ArrayList<String>();
        for (var structField : structFields) {
            if (structField.wasConfiguredToDoIndexing()) {
                result.add(structField.getName());
            }
            result.addAll(getStructFieldsWithIndex(structField.getStructFields()));
        }
        return result;
    }
}
