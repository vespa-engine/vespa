// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.Search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

/**
 * A document summary definition - a list of summary fields.
 *
 * @author bratseth
 */
public class DocumentSummary extends FieldView {

    private boolean fromDisk = false;
    private boolean omitSummaryFeatures = false;
    private String inherited;

    private final Search owner;

    /** Creates a DocumentSummary with the given name. */
    public DocumentSummary(String name, Search owner) {
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
        var parent = getInherited();
        if (parent != null) {
            return parent.getSummaryField(name);
        }
        return (SummaryField) get(name);
    }

    public Collection<SummaryField> getSummaryFields() {
        var fields = new ArrayList<SummaryField>(getFields().size());
        var parent = getInherited();
        if (parent != null) {
            fields.addAll(parent.getSummaryFields());
        }
        for (var field : getFields()) {
            fields.add((SummaryField) field);
        }
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
        for (SummaryField summaryField : getSummaryFields() ) {
            if (summaryField.isImplicit()) continue;
            for (Iterator<SummaryField.Source> j = summaryField.sourceIterator(); j.hasNext(); ) {
                String sourceName = j.next().getName();
                if (sourceName.equals(summaryField.getName())) continue;
                SummaryField sourceField=getSummaryField(sourceName);
                if (sourceField==null) continue;
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
        this.inherited = inherited;
    }

    /** Returns the parent of this, or null if none is inherited */
    public DocumentSummary getInherited() {
        return owner.getSummary(inherited);
    }

    /** Returns the name of the summary this was declared to inherit, or null if not sett to inherit anything */
    public String getInheritedName() {
        return inherited;
    }

    @Override
    public String toString() {
        return "document summary '" + getName() + "'";
    }

    public void validate(DeployLogger logger) {
        if (inherited != null) {
            if ( ! owner.getSummaries().containsKey(inherited)) {
                logger.log(Level.WARNING,
                           this + " inherits " + inherited + " but this" + " is not present in " + owner);
                logger.logApplicationPackage(Level.WARNING,
                                             this + " inherits " + inherited + " but this" + " is not present in " + owner);
                // TODO: When safe, replace the above by
                // throw new IllegalArgumentException(this + " inherits " + inherited + " but this" +
                //                                   " is not present in " + owner);
                // ... and update SummaryTestCase.testValidationOfInheritedSummary
            }
        }

    }

}
