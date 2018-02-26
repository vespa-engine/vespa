// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.indexinglanguage.expressions.AttributeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.ForEachExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SummaryExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ZCurveExpression;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

/**
 * Adds a "fieldName.zcurve" long attribute and a "fieldName.distance" summary field to all position type fields.
 *
 * @author bratseth
 */
public class CreatePositionZCurve extends Processor {

    public CreatePositionZCurve(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        for (SDField field : search.allConcreteFields()) {
            DataType fieldType = field.getDataType();
            if ( ! isSupportedPositionType(fieldType)) continue;

            if (validate && field.doesIndexing()) {
                fail(search, field, "Indexing of data type '" + fieldType.getName() + "' is not supported, " +
                                    "replace 'index' statement with 'attribute'.");
            }

            if ( ! field.doesAttributing()) continue;

            boolean doesSummary = field.doesSummarying();

            String fieldName = field.getName();
            field.getAttributes().remove(fieldName);

            String zName = PositionDataType.getZCurveFieldName(fieldName);
            SDField zCurveField = createZCurveField(field, zName, validate);
            search.addExtraField(zCurveField);
            search.fieldSets().addBuiltInFieldSetItem(BuiltInFieldSets.INTERNAL_FIELDSET_NAME, zCurveField.getName());

            // configure summary
            Collection<String> summaryTo = removeSummaryTo(field);
            ensureCompatibleSummary(field, zName,
                                    PositionDataType.getPositionSummaryFieldName(fieldName),
                                    DataType.getArray(DataType.STRING), // will become "xmlstring"
                                    SummaryTransform.POSITIONS, summaryTo, validate);
            ensureCompatibleSummary(field, zName,
                                    PositionDataType.getDistanceSummaryFieldName(fieldName),
                                    DataType.INT,
                                    SummaryTransform.DISTANCE, summaryTo, validate);
            // clear indexing script
            field.setIndexingScript(null);
            SDField posX = field.getStructField(PositionDataType.FIELD_X);
            if (posX != null) {
                posX.setIndexingScript(null);
            }
            SDField posY = field.getStructField(PositionDataType.FIELD_Y);
            if (posY != null) {
                posY.setIndexingScript(null);
            }
            if (doesSummary) ensureCompatibleSummary(field, zName,
                                                     field.getName(),
                                                     field.getDataType(),
                                                     SummaryTransform.GEOPOS, summaryTo, validate);
        }
    }

    private SDField createZCurveField(SDField inputField, String fieldName, boolean validate) {
        if (validate && search.getConcreteField(fieldName) != null || search.getAttribute(fieldName) != null) {
            throw newProcessException(search, null, "Incompatible position attribute '" + fieldName +
                                                    "' already created.");
        }
        boolean isArray = inputField.getDataType() instanceof ArrayDataType;
        SDField field = new SDField(fieldName, isArray ? DataType.getArray(DataType.LONG) : DataType.LONG);
        Attribute attribute = new Attribute(fieldName, Attribute.Type.LONG, isArray ? Attribute.CollectionType.ARRAY :
                                                                            Attribute.CollectionType.SINGLE);
        attribute.setPosition(true);
        attribute.setFastSearch(true);
        field.addAttribute(attribute);

        ScriptExpression script = inputField.getIndexingScript();
        script = (ScriptExpression)new RemoveSummary(inputField.getName()).convert(script);
        script = (ScriptExpression)new PerformZCurve(field, fieldName).convert(script);
        field.setIndexingScript(script);
        return field;
    }

    private void ensureCompatibleSummary(SDField field, String sourceName, String summaryName, DataType summaryType,
                                         SummaryTransform summaryTransform, Collection<String> summaryTo, boolean validate) {
        SummaryField summary = search.getSummaryField(summaryName);
        if (summary == null) {
            summary = new SummaryField(summaryName, summaryType, summaryTransform);
            summary.addDestination("default");
            summary.addDestinations(summaryTo);
            field.addSummaryField(summary);
        } else if (!summary.getDataType().equals(summaryType)) {
            if (validate)
                fail(search, field, "Incompatible summary field '" + summaryName + "' type "+summary.getDataType()+" already created.");
        } else if (summary.getTransform() == SummaryTransform.NONE) {
            summary.setTransform(summaryTransform);
            summary.addDestination("default");
            summary.addDestinations(summaryTo);
        } else if (summary.getTransform() != summaryTransform) {
            deployLogger.log(Level.WARNING, "Summary field " + summaryName + " has wrong transform: " + summary.getTransform());
            return;
        }
        SummaryField.Source source = new SummaryField.Source(sourceName);
        summary.getSources().clear();
        summary.addSource(source);
    }

    private Set<String> removeSummaryTo(SDField field) {
        Set<String> summaryTo = new HashSet<>();
        Collection<SummaryField> summaryFields = field.getSummaryFields();
        for (SummaryField summary : summaryFields) {
            summaryTo.addAll(summary.getDestinations());
        }
        summaryFields.clear();
        return summaryTo;
    }

    private static boolean isSupportedPositionType(DataType dataType) {
        if (dataType instanceof ArrayDataType) {
            dataType = ((ArrayDataType)dataType).getNestedType();
        }
        return dataType.equals(PositionDataType.INSTANCE);
    }

    private static class RemoveSummary extends ExpressionConverter {

        final String find;

        RemoveSummary(String find) {
            this.find = find;
        }

        @Override
        protected boolean shouldConvert(Expression exp) {
            if (!(exp instanceof SummaryExpression)) {
                return false;
            }
            String fieldName = ((SummaryExpression)exp).getFieldName();
            return fieldName == null || fieldName.equals(find);
        }

        @Override
        protected Expression doConvert(Expression exp) {
            return null;
        }
    }

    private static class PerformZCurve extends ExpressionConverter {

        final String find;
        final String replace;
        final boolean isArray;

        PerformZCurve(SDField find, String replace) {
            this.find = find.getName();
            this.replace = replace;
            this.isArray = find.getDataType() instanceof ArrayDataType;
        }

        @Override
        protected boolean shouldConvert(Expression exp) {
            if (!(exp instanceof AttributeExpression)) {
                return false;
            }
            String fieldName = ((AttributeExpression)exp).getFieldName();
            return fieldName == null || fieldName.equals(find);
        }

        @Override
        protected Expression doConvert(Expression exp) {
            return new StatementExpression(
                    isArray ? new ForEachExpression(new ZCurveExpression()) :
                    new ZCurveExpression(), new AttributeExpression(replace));
        }
    }

}
