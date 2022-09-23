// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.prelude.fastsearch.DocsumDefinitionSet;
import com.yahoo.schema.Schema;
import com.yahoo.schema.processing.DynamicSummaryTransformUtils;
import com.yahoo.vespa.config.search.SummaryConfig;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;

import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;

/**
 * A summary derived from a search definition.
 * Each summary definition have at least one summary, the default
 * which has the same name as the search definition.
 *
 * @author bratseth
 */
public class SummaryClass extends Derived {

    public static final String DOCUMENT_ID_FIELD = "documentid";

    private final int id;

    /** True if this summary class needs to access summary information on disk */
    private boolean accessingDiskSummary = false;
    private final boolean rawAsBase64;
    private final boolean omitSummaryFeatures;

    /** The summary fields of this indexed by name */
    private final Map<String, SummaryClassField> fields;

    private final DeployLogger deployLogger;

    /**
     * Creates a summary class from a search definition summary
     *
     * @param deployLogger a {@link DeployLogger}
     */
    public SummaryClass(Schema schema, DocumentSummary summary, DeployLogger deployLogger) {
        super(summary.getName());
        this.deployLogger = deployLogger;
        this.rawAsBase64 = schema.isRawAsBase64();
        this.omitSummaryFeatures = summary.omitSummaryFeatures();
        Map<String, SummaryClassField> fields = new java.util.LinkedHashMap<>();
        deriveFields(schema, summary, fields);
        deriveImplicitFields(summary, fields);
        this.fields = Collections.unmodifiableMap(fields);
        this.id = deriveId(summary.getName(), fields);
    }

    public int id() { return id; }

    /** MUST be called after all other fields are added */
    private void deriveImplicitFields(DocumentSummary summary, Map<String, SummaryClassField> fields) {
        if (summary.getName().equals("default")) {
            addField(SummaryClass.DOCUMENT_ID_FIELD, DataType.STRING, SummaryTransform.DOCUMENT_ID, "", fields);
        }
    }

    private void deriveFields(Schema schema, DocumentSummary summary, Map<String, SummaryClassField> fields) {
        for (SummaryField summaryField : summary.getSummaryFields().values()) {
            if (!accessingDiskSummary && schema.isAccessingDiskSummary(summaryField)) {
                accessingDiskSummary = true;
            }
            addField(summaryField.getName(), summaryField.getDataType(), summaryField.getTransform(),
                    getSource(summaryField), fields);
        }
    }

    private void addField(String name, DataType type,
                          SummaryTransform transform,
                          String source,
                          Map<String, SummaryClassField> fields) {
        if (fields.containsKey(name)) {
            SummaryClassField sf = fields.get(name);
            if ( SummaryClassField.convertDataType(type, transform, rawAsBase64) != sf.getType()) {
                deployLogger.logApplicationPackage(Level.WARNING, "Conflicting definition of field " + name +
                                                                  ". " + "Declared as type " + sf.getType() + " and " + type);
            }
        } else {
            fields.put(name, new SummaryClassField(name, type, transform, source, rawAsBase64));
        }
    }

    public Map<String, SummaryClassField> fields() { return fields; }

    private static int deriveId(String name, Map<String, SummaryClassField> fields) {
        int hash = name.hashCode();
        int number = 1;
        for (var field : fields.values()) {
            hash += number++ * (field.getName().hashCode() +
                                17 * field.getType().getName().hashCode());
        }
        hash = Math.abs(hash);
        if (hash == DocsumDefinitionSet.SLIME_MAGIC_ID)
            hash++;
        return hash;
    }

    public SummaryConfig.Classes.Builder getSummaryClassConfig() {
        SummaryConfig.Classes.Builder classBuilder = new SummaryConfig.Classes.Builder();
        classBuilder.
            id(id).
            name(getName()).
            omitsummaryfeatures(omitSummaryFeatures);
        for (SummaryClassField field : fields.values() ) {
            classBuilder.fields(new SummaryConfig.Classes.Fields.Builder().
                    name(field.getName()).
                    command(field.getCommand()).
                    source(field.getSource()));
        }
        return classBuilder;
    }

    @Override
    public int hashCode() { return id; }

    @Override
    protected String getDerivedName() { return "summary"; }

    @Override
    public String toString() {
        return "summary class '" + getName() + "'";
    }

    /** Returns the command name of a transform */
    static String getCommand(SummaryTransform transform) {
        if (transform == SummaryTransform.NONE) {
            return "";
        } else if (transform == SummaryTransform.DISTANCE) {
            return "absdist";
        } else if (transform.isDynamic()) {
            return "dynamicteaser";
        } else {
            return transform.getName();
        }
    }

    static String getSource(SummaryField summaryField) {
        if (summaryField.getTransform() == SummaryTransform.NONE) {
            return "";
        }

        if (summaryField.getTransform() == SummaryTransform.ATTRIBUTE ||
                (summaryField.getTransform() == SummaryTransform.ATTRIBUTECOMBINER && summaryField.hasExplicitSingleSource()) ||
                summaryField.getTransform() == SummaryTransform.COPY ||
                summaryField.getTransform() == SummaryTransform.DISTANCE ||
                summaryField.getTransform() == SummaryTransform.GEOPOS ||
                summaryField.getTransform() == SummaryTransform.POSITIONS ||
                summaryField.getTransform() == SummaryTransform.MATCHED_ELEMENTS_FILTER ||
                summaryField.getTransform() == SummaryTransform.MATCHED_ATTRIBUTE_ELEMENTS_FILTER)
        {
            return summaryField.getSingleSource();
        } else if (summaryField.getTransform().isDynamic()) {
            return DynamicSummaryTransformUtils.getSource(summaryField);
        } else {
            return "";
        }
    }

    /**
     * A dynamic transform that needs the query to perform its computations.
     * We need this because some model information is shared through configs instead of model - see usage
     */
    static boolean commandRequiringQuery(String commandName) {
        return (commandName.equals("dynamicteaser") ||
                commandName.equals(SummaryTransform.MATCHED_ELEMENTS_FILTER.getName()) ||
                commandName.equals(SummaryTransform.MATCHED_ATTRIBUTE_ELEMENTS_FILTER.getName()));
    }

}
