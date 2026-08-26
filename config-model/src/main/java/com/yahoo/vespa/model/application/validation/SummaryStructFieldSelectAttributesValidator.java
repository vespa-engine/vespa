// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.SchemaInfo;
import com.yahoo.text.Text;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.application.validation.Validation.Context;
import com.yahoo.vespa.model.search.SearchCluster;

/**
 * Validates that a struct field selection ("struct-field" in a document-summary) can be honoured.
 *
 * Streaming search applies a selection when filling the summary field and needs no attributes for it.
 * Everywhere else the selection is applied by the attribute combiner, which requires every selected
 * sub-field to be a struct field attribute; without that the selection would silently have no effect.
 * Whether that holds is known once the transform has been assigned: a summary field which kept a
 * selection without becoming an attribute combiner is one whose selection cannot be applied.
 *
 * This is a model validator rather than a schema processor because only the cluster knows which mode a
 * schema is used in, and the same schema may be used by clusters in different modes.
 *
 * @author arnej
 */
public class SummaryStructFieldSelectAttributesValidator implements Validator {

    @Override
    public void validate(Context context) {
        for (SearchCluster cluster : context.model().getSearchClusters()) {
            for (SchemaInfo spec : cluster.schemas().values()) {
                if (spec.getIndexMode() != SchemaInfo.IndexMode.STREAMING) {
                    validateSelections(context, cluster.getClusterName(), spec.fullSchema());
                }
            }
        }
    }

    private static void validateSelections(Context context, String clusterName, Schema schema) {
        for (DocumentSummary summary : schema.getSummaries().values()) {
            for (SummaryField field : summary.getSummaryFields().values()) {
                if (!field.getStructFields().isEmpty() &&
                    field.getTransform() != SummaryTransform.ATTRIBUTECOMBINER)
                {
                    context.illegal(getErrorMessage(clusterName, schema, summary, field));
                }
            }
        }
    }

    private static String getErrorMessage(String clusterName, Schema schema, DocumentSummary summary,
                                          SummaryField field) {
        return Text.format("For cluster '%s', schema '%s', document-summary '%s', summary field '%s': " +
                           "the selected struct fields %s of source field '%s' cannot all be used as struct " +
                           "field attributes, which is what selecting a subset of them requires here. " +
                           "Add 'indexing: attribute' for the selected struct fields, or remove the selection. " +
                           "Only streaming search can apply a selection without struct field attributes",
                           clusterName, schema.getName(), summary.name(), field.getName(),
                           field.getStructFields(), field.getSingleSource());
    }

}
