// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.Schema;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.vespa.config.search.SummarymapConfig;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;

import java.util.Collections;
import java.util.Map;

/**
 * A summary map (describing search-time summary field transformations)
 * derived from a Schema.
 *
 * @author bratseth
 */
public class SummaryMap extends Derived implements SummarymapConfig.Producer {

    private final Map<String, FieldResultTransform> resultTransforms = new java.util.LinkedHashMap<>();

    /** Creates a summary map from a search definition */
    SummaryMap(Schema schema) {
        derive(schema);
    }

    protected void derive(Schema schema) {
        for (DocumentSummary documentSummary : schema.getSummaries().values()) {
            derive(documentSummary);
        }
        super.derive(schema);
    }

    @Override
    protected void derive(ImmutableSDField field, Schema schema) {
    }

    private void derive(DocumentSummary documentSummary) {
        for (SummaryField summaryField : documentSummary.getSummaryFields().values()) {
            if (summaryField.getTransform()== SummaryTransform.NONE) continue;

            if (summaryField.getTransform()==SummaryTransform.ATTRIBUTE ||
                summaryField.getTransform()==SummaryTransform.DISTANCE ||
                summaryField.getTransform()==SummaryTransform.GEOPOS ||
                summaryField.getTransform()==SummaryTransform.POSITIONS ||
                summaryField.getTransform()==SummaryTransform.MATCHED_ELEMENTS_FILTER ||
                summaryField.getTransform()==SummaryTransform.MATCHED_ATTRIBUTE_ELEMENTS_FILTER)
            {
                resultTransforms.put(summaryField.getName(), new FieldResultTransform(summaryField.getName(),
                                                                                      summaryField.getTransform(),
                                                                                      summaryField.getSingleSource()));
            } else {
                // Note: Currently source mapping is handled in the indexing statement,
                // by creating a summary field for each of the values
                // This works, but is suboptimal. We could consolidate to a minimal set and
                // use the right value from the minimal set as the third parameter here,
                // and add "override" commands to multiple static values
                boolean useFieldNameAsArgument = summaryField.getTransform().isDynamic() || summaryField.getTransform() == SummaryTransform.TEXTEXTRACTOR;
                resultTransforms.put(summaryField.getName(), new FieldResultTransform(summaryField.getName(),
                                                                                      summaryField.getTransform(),
                                                                                      useFieldNameAsArgument ? summaryField.getName() : ""));
            }
        }
    }

    /** Returns a read-only iterator of the FieldResultTransforms of this summary map */
    public Map<String, FieldResultTransform> resultTransforms() {
        return Collections.unmodifiableMap(resultTransforms);
    }

    protected String getDerivedName() { return "summarymap"; }

    /** Returns the command name of a transform */
    private String getCommand(SummaryTransform transform) {
        if (transform == SummaryTransform.DISTANCE)
            return "absdist";
        else if (transform.isDynamic())
            return "dynamicteaser";
        else
            return transform.getName();
    }

    /**
     * Does this summary command name stand for a dynamic transform?
     * We need this because some model information is shared through configs instead of model - see usage
     * A dynamic transform needs the query to perform its computations.
     */
    // TODO/Note: "dynamic" here means something else than in SummaryTransform
    public static boolean isDynamicCommand(String commandName) {
        return (commandName.equals("dynamicteaser") ||
                commandName.equals(SummaryTransform.MATCHED_ELEMENTS_FILTER.getName()) ||
                commandName.equals(SummaryTransform.MATCHED_ATTRIBUTE_ELEMENTS_FILTER.getName()));
    }

    @Override
    public void getConfig(SummarymapConfig.Builder builder) {
        builder.defaultoutputclass(-1);
        for (FieldResultTransform frt : resultTransforms.values()) {
            SummarymapConfig.Override.Builder oB = new SummarymapConfig.Override.Builder()
                .field(frt.getFieldName())
                .command(getCommand(frt.getTransform()))
                .arguments(frt.getArgument());
            builder.override(oB);
        }
    }

}
