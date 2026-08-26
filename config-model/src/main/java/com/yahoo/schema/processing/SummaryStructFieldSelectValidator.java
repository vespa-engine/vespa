// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.yahoo.schema.document.ComplexAttributeFieldUtils.localStructFieldName;

/**
 * Iterates all summary fields selecting a subset of the struct fields of their source ("struct-field" in a
 * document-summary) and validates that the source field type supports it, that the selection is not
 * combined with a summary transform which would ignore it, and that the selected struct fields exist.
 *
 * @author arnej
 */
public class SummaryStructFieldSelectValidator extends Processor {

    public SummaryStructFieldSelectValidator(Schema schema, DeployLogger deployLogger,
                                             RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if (!validate) return;

        for (var summary : schema.getSummaries().values()) {
            for (var field : summary.getSummaryFields().values()) {
                if (!field.getStructFields().isEmpty()) {
                    processSummaryField(summary, field);
                }
            }
        }
    }

    private void processSummaryField(DocumentSummary summary, SummaryField field) {
        var sourceField = schema.getField(field.getSingleSource());
        if (sourceField == null) return; // this case is handled in SummaryFieldsMustHaveValidSource

        if (!isArrayOfStructOrMap(sourceField)) {
            fail(summary, field, "'struct-field' is not supported for this field type. " +
                                 "Supported field types are: array of struct, map of primitive type to struct, " +
                                 "and map of primitive type to primitive type");
        }
        // Only the attribute combiner transform makes use of the selection. It is added later (in
        // ImplicitSummaries or AdjustSummaryTransforms), so the transform is still NONE here unless the
        // user asked for one, or the field is already known to be an attribute combiner. Any other
        // transform would silently ignore the selection.
        var transform = field.getTransform();
        if (transform != SummaryTransform.NONE && transform != SummaryTransform.ATTRIBUTECOMBINER) {
            fail(summary, field, "'struct-field' cannot be combined with the '" + transform.getName() +
                                 "' summary transform");
        }
        var validNames = selectableNames(sourceField);
        for (String name : field.getStructFields()) {
            if (!validNames.contains(name)) {
                fail(summary, field, "struct-field '" + name + "' is not a field of '" + sourceField.getName() +
                                     "', expected one of " + validNames);
            }
        }
    }

    private static boolean isArrayOfStructOrMap(ImmutableSDField field) {
        var type = field.getDataType();
        if (type instanceof ArrayDataType arrayType) {
            return arrayType.getNestedType() instanceof StructDataType;
        }
        return type instanceof MapDataType;
    }

    /**
     * The struct field names which may be selected for the given source field: the sub-fields of the struct
     * for an array of struct, and "key" plus the value (or its sub-fields, as "value.&lt;name&gt;") for a map.
     */
    private static Set<String> selectableNames(ImmutableSDField field) {
        var names = new LinkedHashSet<String>();
        if (field.getDataType() instanceof MapDataType mapType) {
            names.add("key");
            if (mapType.getValueType() instanceof StructDataType) {
                for (var valueField : field.getStructField("value").getStructFields()) {
                    names.add(localStructFieldName(field, valueField));
                }
            } else {
                names.add("value");
            }
        } else {
            for (var structField : field.getStructFields()) {
                names.add(localStructFieldName(field, structField));
            }
        }
        return names;
    }

    private void fail(DocumentSummary summary, SummaryField field, String msg) {
        throw new IllegalArgumentException("For " + schema + ", document-summary '" + summary.name() +
                                           "', summary field '" + field.getName() + "': " + msg);
    }

}
