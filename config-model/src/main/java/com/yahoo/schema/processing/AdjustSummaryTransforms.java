// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.SummaryClass;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import static com.yahoo.schema.document.ComplexAttributeFieldUtils.isComplexFieldWithOnlyStructFieldAttributes;

/**
 * Adds the corresponding summary transform for all "documentid" summary fields.
 * For summary fields without an existing transform:
 * - Adds the attribute transforms  where the source field has an attribute vector.
 * - Adds the attribute combiner transform where the source field is a struct field where all subfields have attribute
 *   vector.
 * - Add the copy transform where the source field is a struct or map field with a different name.
 *
 * @author geirst
 */
public class AdjustSummaryTransforms extends Processor {

    public AdjustSummaryTransforms(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (var summary : schema.getSummaries().values()) {
            for (var summaryField : summary.getSummaryFields().values()) {
                makeDocumentIdTransformIfAppropriate(summaryField);
                makeAttributeTransformIfAppropriate(summaryField, schema);
                makeAttributeCombinerTransformIfAppropriate(summaryField, schema);
                makeAttributeTokensTransformIfAppropriate(summaryField, summary.getName(), schema);
                makeCopyTransformIfAppropriate(summaryField, schema);
            }
        }
    }

    private void makeDocumentIdTransformIfAppropriate(SummaryField summaryField)
    {
        if (summaryField.getName().equals(SummaryClass.DOCUMENT_ID_FIELD)) {
            summaryField.setTransform(SummaryTransform.DOCUMENT_ID);
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

    private void makeAttributeTokensTransformIfAppropriate(SummaryField summaryField, String docsumName, Schema schema) {
        if (summaryField.getTransform() == SummaryTransform.TOKENS) {
            String sourceFieldName = summaryField.getSingleSource();
            Attribute attribute = schema.getAttribute(sourceFieldName);
            ImmutableSDField source = schema.getField(sourceFieldName);
            if (!source.doesIndexing()) {
                if (attribute != null) {
                    summaryField.setTransform(SummaryTransform.ATTRIBUTE_TOKENS);
                } else {
                    throw new IllegalArgumentException("For schema '" + schema.getName() +
                            "', document-summary '" + docsumName +
                            "', summary field '" + summaryField.getName() +
                            "', source field '" + sourceFieldName +
                            "': tokens summary field setting requires index or attribute for source field");
                }
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
}
