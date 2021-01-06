// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.document.DataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.ComplexAttributeFieldUtils;
import com.yahoo.searchdefinition.document.ImmutableSDField;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.search.AbstractSearchCluster;
import com.yahoo.vespa.model.search.SearchCluster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Validates that complex fields (of type struct or map) that have struct field attributes are supported.
 *
 * Only applies for indexed search clusters.
 *
 * @author geirst
 */
public class ComplexAttributeFieldsValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        List<AbstractSearchCluster> searchClusters = model.getSearchClusters();
        for (AbstractSearchCluster cluster : searchClusters) {
            if (cluster.isStreaming()) {
                continue;
            }
            SearchCluster searchCluster = (SearchCluster) cluster;
            for (AbstractSearchCluster.SchemaSpec spec : searchCluster.getLocalSDS()) {
                validateComplexFields(searchCluster.getClusterName(), spec.getSearchDefinition().getSearch());
            }
        }
    }

    private static void validateComplexFields(String clusterName, Search search) {
        String unsupportedFields = search.allFields()
                .filter(field -> isUnsupportedComplexField(field))
                .map(ComplexAttributeFieldsValidator::toString)
                .collect(Collectors.joining(", "));

        if (!unsupportedFields.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("For cluster '%s', search '%s': The following complex fields do not support using struct field attributes: %s. " +
                                  "Only supported for the following complex field types: array or map of struct with primitive types, map of primitive types",
                            clusterName, search.getName(), unsupportedFields));
        }
    }

    private static boolean isUnsupportedComplexField(ImmutableSDField field) {
        return (field.usesStructOrMap() &&
                !isSupportedComplexField(field) &&
                hasStructFieldAttributes(field.getStructFields()));
    }

    private static boolean isSupportedComplexField(ImmutableSDField field) {
        return (ComplexAttributeFieldUtils.isSupportedComplexField(field) ||
                field.getDataType().equals(PositionDataType.INSTANCE) ||
                field.getDataType().equals(DataType.getArray(PositionDataType.INSTANCE)));
    }

    private static String toString(ImmutableSDField field) {
        return field.getName() + " (" + String.join(", ", getStructFieldAttributes(field.getStructFields(), false)) + ")";
    }

    private static boolean hasStructFieldAttributes(Collection<? extends ImmutableSDField> structFields) {
        return !getStructFieldAttributes(structFields, true).isEmpty();
    }

    private static List<String> getStructFieldAttributes(Collection<? extends ImmutableSDField> structFields,
                                                         boolean returnAllTypes) {
        var result = new ArrayList<String>();
        for (var structField : structFields) {
            for (var attr : structField.getAttributes().values()) {
                if (returnAllTypes || !ComplexAttributeFieldUtils.isPrimitiveType(attr)) {
                    result.add(attr.getName());
                }
            }
            if (structField.usesStructOrMap() && structField.wasConfiguredToDoAttributing()) {
                result.add(structField.getName());
            }
            result.addAll(getStructFieldAttributes(structField.getStructFields(), returnAllTypes));
        }
        return result;
    }
}
