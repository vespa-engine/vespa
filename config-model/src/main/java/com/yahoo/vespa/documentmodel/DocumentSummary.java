// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.Schema;

import java.util.ArrayList;
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

    private int id;
    private boolean fromDisk = false;
    private boolean omitSummaryFeatures = false;
    private Optional<String> inherited = Optional.empty();

    private final Schema owner;

    /** Creates a DocumentSummary with the given name. */
    public DocumentSummary(String name, Schema owner) {
        super(name);
        this.owner = owner;
    }

    public int id() { return id; }

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
        return inherited().get().getSummaryField(name);
    }

    public Map<String, SummaryField> getSummaryFields() {
        var fields = new LinkedHashMap<String, SummaryField>(getFields().size());
        inherited().ifPresent(inherited -> fields.putAll(inherited.getSummaryFields()));
        for (var field : getFields())
            fields.put(field.getName(), (SummaryField) field);
        return fields;
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

    /** Sets the parent of this. Both summaries must be present in the same search definition */
    public void setInherited(String inherited) {
        this.inherited = Optional.of(inherited);
    }

    /** Returns the parent of this, if any */
    public Optional<DocumentSummary> inherited() {
        return inherited.map(name -> owner.getSummary(name));
    }

    @Override
    public String toString() {
        return "document summary '" + getName() + "'";
    }

    public void validate(DeployLogger logger) {
        if (inherited.isPresent()) {
            if ( ! owner.getSummaries().containsKey(inherited.get())) {
                logger.log(Level.WARNING,
                           this + " inherits " + inherited.get() + " but this" + " is not present in " + owner);
                logger.logApplicationPackage(Level.WARNING,
                                             this + " inherits " + inherited.get() + " but this" + " is not present in " + owner);
                // TODO: When safe, replace the above by
                // throw new IllegalArgumentException(this + " inherits " + inherited.get() + " but this" +
                //                                   " is not present in " + owner);
                // ... and update SummaryTestCase.testValidationOfInheritedSummary
            }
        }

    }

}
