// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import static com.yahoo.schema.document.ComplexAttributeFieldUtils.isComplexFieldWithOnlyStructFieldAttributes;

/**
 * Ensure that summary field transforms for fields having the same name
 * are consistent across summary classes
 *
 * @author bratseth
 */
public class SummaryConsistency extends Processor {

    public SummaryConsistency(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (DocumentSummary summary : schema.getSummaries().values()) {
            if (summary.getName().equals("default")) continue;

            for (SummaryField summaryField : summary.getSummaryFields().values()) {
                assertConsistency(summaryField, schema, validate);
                makeAttributeTransformIfAppropriate(summaryField, schema);
                makeAttributeCombinerTransformIfAppropriate(summaryField, schema);
                makeCopyTransformIfAppropriate(summaryField, schema);
            }
        }
    }

    private void assertConsistency(SummaryField summaryField, Schema schema, boolean validate) {
        // Compare to default:
        SummaryField existingDefault = schema.getSummariesInThis().get("default").getSummaryField(summaryField.getName());
        if (existingDefault != null) {
            if (validate)
                assertConsistentTypes(existingDefault, summaryField);
            makeConsistentWithDefaultOrThrow(existingDefault, summaryField);
        }
        else {
            // If no default, compare to whichever definition of the field
            SummaryField existing = schema.getExplicitSummaryField(summaryField.getName());
            if (existing == null) return;
            if (validate)
                assertConsistentTypes(existing, summaryField);
            makeConsistentOrThrow(existing, summaryField, schema);
        }
    }

    /** If the source is an attribute, make this use the attribute transform */
    private void makeAttributeTransformIfAppropriate(SummaryField summaryField, Schema schema) {
        if (summaryField.getTransform() != SummaryTransform.NONE) return;
        Attribute attribute = schema.getAttribute(summaryField.getSingleSource());
        if (attribute == null) return;
        summaryField.setTransform(SummaryTransform.ATTRIBUTE);
    }

    /** If the source is a complex field with only struct field attributes then make this use the attribute combiner transform */
    private void makeAttributeCombinerTransformIfAppropriate(SummaryField summaryField, Schema schema) {
        if (summaryField.getTransform() == SummaryTransform.NONE) {
            String sourceFieldName = summaryField.getSingleSource();
            ImmutableSDField source = schema.getField(sourceFieldName);
            if (source != null && isComplexFieldWithOnlyStructFieldAttributes(source)) {
                summaryField.setTransform(SummaryTransform.ATTRIBUTECOMBINER);
            }
        }
    }

    /*
     * This function must be called after makeAttributeCombinerTransformIfAppropriate().
     */
    private void makeCopyTransformIfAppropriate(SummaryField summaryField, Schema schema) {
        if (summaryField.getTransform() == SummaryTransform.NONE) {
            String sourceFieldName = summaryField.getSingleSource();
            ImmutableSDField source = schema.getField(sourceFieldName);
            if (source != null && source.usesStructOrMap() && summaryField.hasExplicitSingleSource()) {
                summaryField.setTransform(SummaryTransform.COPY);
            }
        }
    }

    private void assertConsistentTypes(SummaryField existing, SummaryField seen) {
        if (existing.getDataType() instanceof WeightedSetDataType && seen.getDataType() instanceof WeightedSetDataType &&
            ((WeightedSetDataType)existing.getDataType()).getNestedType().equals(((WeightedSetDataType)seen.getDataType()).getNestedType()))
            return; // Disregard create-if-nonexistent and create-if-zero distinction
        if ( ! compatibleTypes(seen.getDataType(), existing.getDataType()))
            throw new IllegalArgumentException(existing.toLocateString() + " is inconsistent with " + 
                                               seen.toLocateString() + ": All declarations of the same summary field must have the same type");
    }

    private boolean compatibleTypes(DataType summaryType, DataType existingType) {
        if (summaryType instanceof TensorDataType && existingType instanceof TensorDataType) {
            return summaryType.isAssignableFrom(existingType); // TODO: Just do this for all types
        }
        return summaryType.equals(existingType);
    }

    private void makeConsistentOrThrow(SummaryField field1, SummaryField field2, Schema schema) {
        if (field2.getTransform() == SummaryTransform.ATTRIBUTE && field1.getTransform() == SummaryTransform.NONE) {
            Attribute attribute = schema.getAttribute(field1.getName());
            if (attribute != null) {
                field1.setTransform(SummaryTransform.ATTRIBUTE);
            }
        }

        if (field2.getTransform().equals(SummaryTransform.NONE)) {
            field2.setTransform(field1.getTransform());
        }
        else { // New field sets an explicit transform - must be the same
            assertEqualTransform(field1,field2);
        }
    }

    private void makeConsistentWithDefaultOrThrow(SummaryField defaultField, SummaryField newField) {
        if (newField.getTransform().equals(SummaryTransform.NONE)) {
            newField.setTransform(defaultField.getTransform());
        }
        else { // New field sets an explicit transform - must be the same
            assertEqualTransform(defaultField,newField);
        }
    }

    private void assertEqualTransform(SummaryField field1, SummaryField field2) {
        if ( ! field2.getTransform().equals(field1.getTransform())) {
            throw new IllegalArgumentException("Conflicting summary transforms. " + field2 + " is already defined as " +
                                               field1 + ". A field with the same name " +
                                               "can not have different transforms in different summary classes");
        }
    }


}
