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
 * combined with a summary transform which would ignore it, that the selected struct fields exist, and
 * that a selection from a map has a shape which is currently allowed.
 *
 * This is all the validation there is: a selection is applied in every mode, either by the attribute
 * combiner when the selected sub-fields are struct field attributes, or when the summary field is filled
 * from the stored document when they are not. The transforms which can be assigned to a summary field
 * after this all honour a selection, which is why they are restricted here.
 *
 * @author arnej
 */
public class SummaryStructFieldSelectValidator extends Processor {

    /** The name selecting the key of a map. */
    private static final String KEY = "key";

    /** The name selecting the value of a map of primitive type; sub-fields of a struct value are "value.&lt;name&gt;". */
    private static final String VALUE = "value";

    /**
     * Must a selection from a map of primitive type to primitive type name both the key and the value?
     * That is the only selection which gives a map back, but it is also the full set of names which can be
     * selected for such a field, so requiring it would leave the selection with nothing to say. It is
     * therefore not required: naming only the key, or only the value, gives an array of objects holding
     * just that sub-field. Set this to true (and drop the tests covering it) to disallow that again.
     */
    private static final boolean PRIMITIVE_MAP_SELECTION_MUST_BE_KEY_AND_VALUE = false;

    /**
     * For now a selection from a map of primitive type to struct must name the key and at least one
     * sub-field of the value struct, since that is the only shape the backend is known to handle. Set this
     * to false (and drop the tests covering it) when the backend can produce the other shapes.
     */
    private static final boolean STRUCT_MAP_SELECTION_MUST_BE_KEY_AND_VALUE = true;

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
        // The source is resolved the way AdjustSummaryTransforms does it, since that is what decides
        // whether the selection becomes an attribute combiner and is applied at all. Note that
        // SummaryClass.getCombinerShape resolves it differently, falling back to the name of the summary
        // field, because by then an implicit summary field has the struct sub-fields as its sources. The
        // two only disagree for a summary field with several sources or a dotted one, which is not a field
        // a selection can be applied to anyway: there the shape ends up as INFER and the backend deduces
        // it.
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
        if (sourceField.getDataType() instanceof MapDataType mapType) {
            validateMapSelection(summary, field, validNames, mapType);
        }
    }

    /**
     * Validates the temporary restriction that a selection from a map must name the key and at least one
     * field from the value; see {@link #PRIMITIVE_MAP_SELECTION_MUST_BE_KEY_AND_VALUE} and
     * {@link #STRUCT_MAP_SELECTION_MUST_BE_KEY_AND_VALUE}. All the names are known to be valid here, so
     * they are either "key" or the value (as "value" or "value.&lt;name&gt;").
     */
    private void validateMapSelection(DocumentSummary summary, SummaryField field, Set<String> validNames,
                                      MapDataType mapType) {
        boolean restricted = (mapType.getValueType() instanceof StructDataType)
                             ? STRUCT_MAP_SELECTION_MUST_BE_KEY_AND_VALUE
                             : PRIMITIVE_MAP_SELECTION_MUST_BE_KEY_AND_VALUE;
        if (!restricted) return;
        var selected = field.getStructFields();
        if (!selected.contains(KEY)) {
            fail(summary, field, "a 'struct-field' selection for a map must include '" + KEY + "'");
        }
        if (selected.stream().allMatch(KEY::equals)) {
            fail(summary, field, "a 'struct-field' selection for a map must include at least one field " +
                                 "from the value, one of " + valueNames(validNames));
        }
    }

    private static Set<String> valueNames(Set<String> validNames) {
        var names = new LinkedHashSet<>(validNames);
        names.remove(KEY);
        return names;
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
            names.add(KEY);
            if (mapType.getValueType() instanceof StructDataType) {
                for (var valueField : field.getStructField(VALUE).getStructFields()) {
                    names.add(localStructFieldName(field, valueField));
                }
            } else {
                names.add(VALUE);
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
