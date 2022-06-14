// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.GeoPos;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryField.Source;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/*
 * Adjusts position summary fields by adding derived summary fields (.distance and .position) and setting summary
 * transform and source.
 */
public class AdjustPositionSummaryFields extends Processor {

    public AdjustPositionSummaryFields(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    private boolean useV8GeoPositions = false;

    @Override
    public void process(boolean validate, boolean documentsOnly, ModelContext.Properties properties) {
        this.useV8GeoPositions = properties.featureFlags().useV8GeoPositions();
        process(validate, documentsOnly);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (DocumentSummary summary : schema.getSummaries().values()) {
            scanSummary(summary);
        }
    }

    static String getPositionSummaryFieldName(String fieldName) {
        // Only used in v7 legacy mode, remove in Vespa 9
        return fieldName + ".position";
    }

    static String getDistanceSummaryFieldName(String fieldName) {
        // Only used in v7 legacy mode, remove in Vespa 9
        return fieldName + ".distance";
    }

    private void scanSummary(DocumentSummary summary) {
        for (SummaryField summaryField : summary.getSummaryFields().values()) {
            if ( ! GeoPos.isAnyPos(summaryField.getDataType())) continue;

            String originalSource = summaryField.getSingleSource();
            if (originalSource.indexOf('.') == -1) { // Eliminate summary fields with pos.x or pos.y as source
                ImmutableSDField sourceField = schema.getField(originalSource);
                if (sourceField != null) {
                    String zCurve = null;
                    if (sourceField.getDataType().equals(summaryField.getDataType())) {
                        zCurve = PositionDataType.getZCurveFieldName(originalSource);
                    } else if (sourceField.getDataType().equals(makeZCurveDataType(summaryField.getDataType())) &&
                               hasZCurveSuffix(originalSource)) {
                        zCurve = originalSource;
                    }
                    if (zCurve != null) {
                        if (hasPositionAttribute(zCurve)) {
                            Source source = new Source(zCurve);
                            adjustPositionField(summary, summaryField, source);
                        } else if (sourceField.isImportedField() || !summaryField.getName().equals(originalSource)) {
                            fail(summaryField, "No position attribute '" + zCurve + "'");
                        }
                    }
                }
            }
        }
    }

    private void adjustPositionField(DocumentSummary summary, SummaryField summaryField, Source source) {
        summaryField.setTransform(SummaryTransform.GEOPOS);
        summaryField.getSources().clear();
        summaryField.addSource(source);
        ensureSummaryField(summary,
                           getPositionSummaryFieldName(summaryField.getName()),
                           DataType.getArray(DataType.STRING),
                           source,
                           SummaryTransform.POSITIONS);
        ensureSummaryField(summary,
                           getDistanceSummaryFieldName(summaryField.getName()),
                           DataType.INT,
                           source,
                           SummaryTransform.DISTANCE);
    }

    private void ensureSummaryField(DocumentSummary summary, String fieldName, DataType dataType, Source source, SummaryTransform transform) {
        SummaryField oldField = schema.getSummaryField(fieldName);
        if (oldField == null) {
            if (useV8GeoPositions) return;
            SummaryField newField = new SummaryField(fieldName, dataType, transform);
            newField.addSource(source);
            summary.add(newField);
            return;
        }
        if (!oldField.getDataType().equals(dataType)) {
            fail(oldField, "exists with type '" + oldField.getDataType().toString() + "', should be of type '" + dataType.toString() + "'");
        }
        if (oldField.getTransform() != transform) {
            fail(oldField, "has summary transform '" + oldField.getTransform().toString() + "', should have transform '" + transform.toString() + "'");
        }
        if (oldField.getSourceCount() != 1 || !oldField.getSingleSource().equals(source.getName())) {
            fail(oldField, "has source '" + oldField.getSources().toString() + "', should have source '" + source + "'");
        }
        if (useV8GeoPositions) return;
        summary.add(oldField);
    }

    private boolean hasPositionAttribute(String name) {
        Attribute attribute = schema.getAttribute(name);
        if (attribute == null) {
            ImmutableSDField field = schema.getField(name);
            if (field != null && field.isImportedField()) {
                attribute = field.getAttribute();
            }
        }
        return attribute != null && attribute.isPosition();
    }

    private static boolean hasZCurveSuffix(String name) {
        String suffix = PositionDataType.getZCurveFieldName("");
        return name.length() > suffix.length() && name.substring(name.length() - suffix.length()).equals(suffix);
    }

    private static DataType makeZCurveDataType(DataType dataType) {
        return dataType instanceof ArrayDataType ? DataType.getArray(DataType.LONG) : DataType.LONG;
    }

    private void fail(SummaryField summaryField, String msg) {
        throw newProcessException(schema.getName(), summaryField.getName(), msg);
    }

}
