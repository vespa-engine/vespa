// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.ImmutableSDField;
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

    public AdjustPositionSummaryFields(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (DocumentSummary summary : search.getSummaries().values()) {
            scanSummary(summary);
        }
    }

    private void scanSummary(DocumentSummary summary) {
        for (SummaryField summaryField : summary.getSummaryFields()) {
            if (isPositionDataType(summaryField.getDataType())) {
                String originalSource = summaryField.getSingleSource();
                if (originalSource.indexOf('.') == -1) { // Eliminate summary fields with pos.x or pos.y as source
                    ImmutableSDField sourceField = search.getField(originalSource);
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
    }

    private void adjustPositionField(DocumentSummary summary, SummaryField summaryField, Source source) {
        summaryField.setTransform(SummaryTransform.GEOPOS);
        summaryField.getSources().clear();
        summaryField.addSource(source);
        ensureSummaryField(summary,
                           PositionDataType.getPositionSummaryFieldName(summaryField.getName()),
                           DataType.getArray(DataType.STRING),
                           source,
                           SummaryTransform.POSITIONS);
        ensureSummaryField(summary,
                           PositionDataType.getDistanceSummaryFieldName(summaryField.getName()),
                           DataType.INT,
                           source,
                           SummaryTransform.DISTANCE);
    }

    private void ensureSummaryField(DocumentSummary summary, String fieldName, DataType dataType, Source source, SummaryTransform transform) {
        SummaryField oldField = search.getSummaryField(fieldName);
        if (oldField == null) {
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
        summary.add(oldField);
    }

    private boolean hasPositionAttribute(String name) {
        Attribute attribute = search.getAttribute(name);
        if (attribute == null) {
            ImmutableSDField field = search.getField(name);
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

    private static boolean isPositionDataType(DataType dataType) {
        return dataType.equals(PositionDataType.INSTANCE) || dataType.equals(DataType.getArray(PositionDataType.INSTANCE));
    }

    private static DataType makeZCurveDataType(DataType dataType) {
        return dataType instanceof ArrayDataType ? DataType.getArray(DataType.LONG) : DataType.LONG;
    }

    private void fail(SummaryField summaryField, String msg) {
        throw newProcessException(search.getName(), summaryField.getName(), msg);
    }

}
