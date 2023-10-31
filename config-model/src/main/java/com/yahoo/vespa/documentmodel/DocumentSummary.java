// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.Schema;
import com.yahoo.searchlib.rankingexpression.Reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * A document summary definition - a list of summary fields.
 *
 * @author bratseth
 */
public class DocumentSummary extends FieldView {

    private boolean fromDisk = false;
    private boolean omitSummaryFeatures = false;
    private List<String> inherited = new ArrayList<>();

    private final Schema owner;

    /** Creates a DocumentSummary with the given name. */
    public DocumentSummary(String name, Schema owner) {
        super(name);
        this.owner = owner;
    }

    public void setFromDisk(boolean fromDisk) { this.fromDisk = fromDisk; }

    /** Returns whether the user has noted explicitly that this summary accesses disk */
    public boolean isFromDisk() { return fromDisk; }

    public void setOmitSummaryFeatures(boolean value) {
        omitSummaryFeatures = value;
    }

    public boolean omitSummaryFeatures() {
        return omitSummaryFeatures;
    }

    /**
     * The model is constrained to ensure that summary fields of the same name
     * in different classes have the same summary transform, because this is
     * what is supported by the backend currently.
     * 
     * @param summaryField the summaryfield to add
     */
    public void add(SummaryField summaryField) {
        summaryField.addDestination(getName());
        super.add(summaryField);
    }

    public SummaryField getSummaryField(String name) {
        var field = (SummaryField)get(name);
        if (field != null) return field;
        if (inherited().isEmpty()) return null;
        for (var inheritedSummary : inherited()) {
            var inheritedField = inheritedSummary.getSummaryField(name);
            if (inheritedField != null)
                return inheritedField;
        }
        return null;
    }

    public Map<String, SummaryField> getSummaryFields() {
        var allFields = new LinkedHashMap<String, SummaryField>(getFields().size());
        for (var inheritedSummary : inherited()) {
            if (inheritedSummary == null) continue;
            allFields.putAll(inheritedSummary.getSummaryFields());
        }
        for (var field : getFields())
            allFields.put(field.getName(), (SummaryField) field);
        return allFields;
    }

    /**
     * Removes implicit fields which shouldn't be included.
     * This is implicitly added fields which are sources for
     * other fields. We then assume they are not intended to be added
     * implicitly in addition.
     * This should be called when this summary is complete.
     */
    public void purgeImplicits() {
        List<SummaryField> falseImplicits = new ArrayList<>();
        for (SummaryField summaryField : getSummaryFields().values()) {
            if (summaryField.isImplicit()) continue;
            for (Iterator<SummaryField.Source> j = summaryField.sourceIterator(); j.hasNext(); ) {
                String sourceName = j.next().getName();
                if (sourceName.equals(summaryField.getName())) continue;
                SummaryField sourceField=getSummaryField(sourceName);
                if (sourceField == null) continue;
                if (!sourceField.isImplicit()) continue;
                falseImplicits.add(sourceField);
            }
        }
        for (SummaryField field : falseImplicits) {
            remove(field.getName());
        }
    }

    /** Adds a parent of this. Both summaries must be present in the same schema, or a parent schema. */
    public void addInherited(String inherited) {
        this.inherited.add(inherited);
    }

    /** Returns the parent of this, if any */
    public List<DocumentSummary> inherited() {
        return inherited.stream().map(name -> owner.getSummary(name)).toList();
    }

    @Override
    public String toString() {
        return "document-summary '" + getName() + "'";
    }

    public void validate(DeployLogger logger) {
        for (var inheritedName : inherited) {
            var inheritedSummary = owner.getSummary(inheritedName);
            if (inheritedSummary == null) {
                // TODO Vespa 9: Throw IllegalArgumentException instead
                logger.logApplicationPackage(Level.WARNING,
                                             this + " inherits '" + inheritedName + "' but this" + " is not present in " + owner);
            }
        }

    }

}
