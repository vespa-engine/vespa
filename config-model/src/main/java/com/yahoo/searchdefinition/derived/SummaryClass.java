// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.prelude.fastsearch.DocsumDefinitionSet;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.config.search.SummaryConfig;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

/**
 * A summary derived from a search definition.
 * Each summary definition have at least one summary, the default
 * which has the same name as the search definition.
 *
 * @author  bratseth
 */
public class SummaryClass extends Derived {

    public static final String DOCUMENT_ID_FIELD = "documentid";

    /** True if this summary class needs to access summary information on disk */
    private boolean accessingDiskSummary = false;
    private final boolean rawAsBase64;
    private final boolean omitSummaryFeatures;

    /** The summary fields of this indexed by name */
    private Map<String,SummaryClassField> fields = new java.util.LinkedHashMap<>();

    private DeployLogger deployLogger;

    private final Random random = new Random(7);

    /**
     * Creates a summary class from a search definition summary
     *
     * @param deployLogger a {@link DeployLogger}
     */
    public SummaryClass(Search search, DocumentSummary summary, DeployLogger deployLogger) {
        this.deployLogger = deployLogger;
        this.rawAsBase64 = search.isRawAsBase64();
        this.omitSummaryFeatures = summary.omitSummaryFeatures();
        deriveName(summary);
        deriveFields(search,summary);
        deriveImplicitFields(summary);
    }

    private void deriveName(DocumentSummary summary) {
        setName(summary.getName());
    }

    /** MUST be called after all other fields are added */
    private void deriveImplicitFields(DocumentSummary summary) {
        if (summary.getName().equals("default")) {
            addField(SummaryClass.DOCUMENT_ID_FIELD, DataType.STRING);
        }
    }

    private void deriveFields(Search search, DocumentSummary summary) {
        for (SummaryField summaryField : summary.getSummaryFields()) {
            if (!accessingDiskSummary && search.isAccessingDiskSummary(summaryField)) {
                accessingDiskSummary = true;
            }
            addField(summaryField.getName(), summaryField.getDataType(), summaryField.getTransform());
        }
    }

    private void addField(String name, DataType type) {
        addField(name, type, null);
    }

    private void addField(String name, DataType type, SummaryTransform transform) {
        if (fields.containsKey(name)) {
            SummaryClassField sf = fields.get(name);
            if (!SummaryClassField.convertDataType(type, transform, rawAsBase64).equals(sf.getType())) {
                deployLogger.logApplicationPackage(Level.WARNING, "Conflicting definition of field " + name + ". " +
                               "Declared as type " + sf.getType() + " and " + type);
            }
        } else {
            fields.put(name, new SummaryClassField(name, type, transform, rawAsBase64));
        }
    }


    /** Returns an iterator of the fields of this summary. Removes on this iterator removes the field from this summary */
    public Iterator<SummaryClassField> fieldIterator() {
        return fields.values().iterator();
    }

    public void addField(SummaryClassField field) {
        fields.put(field.getName(),field);
    }

    /** Returns the writable map of fields of this summary */ // TODO: Make read only, move writers to iterator/addField
    public Map<String,SummaryClassField> getFields() { return fields; }

    public SummaryClassField getField(String name) {
        return fields.get(name);
    }

    public int getFieldCount() { return fields.size(); }

    public int hashCode() {
        int number = 1;
        int hash = getName().hashCode();
        for (Iterator i = fieldIterator(); i.hasNext(); ) {
            SummaryClassField field = (SummaryClassField)i.next();
            hash += number * (field.getName().hashCode() +
                              17*field.getType().getName().hashCode());
            number++;
        }
        if (hash < 0)
            hash *= -1;
        return hash;
    }

    public SummaryConfig.Classes.Builder getSummaryClassConfig() {
        SummaryConfig.Classes.Builder classBuilder = new SummaryConfig.Classes.Builder();
        int id = hashCode();
        if (id == DocsumDefinitionSet.SLIME_MAGIC_ID) {
            deployLogger.log(Level.WARNING, "Summary class '" + getName() + "' hashes to the SLIME_MAGIC_ID '" + id +
                                            "'. This is unlikely but I autofix it for you by adding a random number.");
            id += random.nextInt();
        }
        classBuilder.
            id(id).
            name(getName()).
            omitsummaryfeatures(omitSummaryFeatures);
        for (SummaryClassField field : fields.values() ) {
            classBuilder.fields(new SummaryConfig.Classes.Fields.Builder().
                    name(field.getName()).
                    type(field.getType().getName()));
        }
        return classBuilder;
    }

    @Override
    protected String getDerivedName() { return "summary"; }

    public String toString() {
        return "summary class " + getName();
    }

}
