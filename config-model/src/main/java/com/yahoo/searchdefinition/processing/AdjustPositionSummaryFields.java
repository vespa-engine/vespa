// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.ImmutableImportedSDField;
import com.yahoo.searchdefinition.document.ImmutableSDField;
import com.yahoo.searchdefinition.document.ImportedField;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryField.Source;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.LinkedList;
import java.util.List;

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
        List<SummaryField> fixupFields = new LinkedList<SummaryField>();
        for (DocumentSummary summary : search.getSummaries().values()) {
            scanSummary(summary, fixupFields);
        }
        for (SummaryField summaryField : fixupFields) {
            String originalSource = summaryField.getSingleSource();
            summaryField.getSources().clear();
            summaryField.addSource(PositionDataType.getZCurveFieldName(originalSource));
        }
    }

    private void scanSummary(DocumentSummary summary, List<SummaryField> fixupFields) {
        for (SummaryField summaryField : summary.getSummaryFields()) {
            if (isPositionDataType(summaryField.getDataType())) {
                String originalSource = summaryField.getSingleSource();
                ImmutableSDField sourceField = getSourceField(originalSource);
                if (sourceField != null && sourceField.getDataType().equals(summaryField.getDataType())) {
                    String zCurve = PositionDataType.getZCurveFieldName(originalSource);
                    if (hasPositionAttribute(zCurve)) {
                        Source source = new Source(zCurve);
                        adjustPositionField(summary, summaryField, source);
                        fixupFields.add(summaryField);
                    } else {
                        fail(summaryField, "No position attribute '" + zCurve + "'");
                    }
                }
            }
        }
    }

    private void adjustPositionField(DocumentSummary summary, SummaryField summaryField, Source source) {
        if (summaryField.getTransform() == SummaryTransform.NONE) {
            summaryField.setTransform(SummaryTransform.GEOPOS);
        }
        ensureSummaryField(summary, PositionDataType.getPositionSummaryFieldName(summaryField.getName()),
                DataType.getArray(DataType.STRING), source, SummaryTransform.POSITIONS);
        ensureSummaryField(summary, PositionDataType.getDistanceSummaryFieldName(summaryField.getName()),
                DataType.INT, source, SummaryTransform.DISTANCE);
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

    private ImmutableSDField getSourceField(String name) {
        ImmutableSDField field = search.getField(name);
        if (field == null && search.importedFields().isPresent()) {
            ImportedField importedField = search.importedFields().get().complexFields().get(name);
            if (importedField != null) {
                field = new ImmutableImportedSDField(importedField);
            }
        }
        return field;
    }

    private boolean hasPositionAttribute(String name) {
        Attribute attribute = search.getAttribute(name);
        if (attribute == null) {
            ImmutableSDField field = search.getField(name);
            if (field != null && field.isImportedField()) {
                while (field.isImportedField()) {
                    field = field.getBackingField();
                }
                attribute = field.getAttributes().get(field.getName());
            }
        }
        return attribute != null && attribute.isPosition();
    }

    private static boolean isPositionDataType(DataType dataType) {
        return dataType.equals(PositionDataType.INSTANCE) || dataType.equals(DataType.getArray(PositionDataType.INSTANCE));
    }

    private void fail(SummaryField summaryField, String msg) {
        throw newProcessException(search.getName(), summaryField.getName(), msg);
    }

}
