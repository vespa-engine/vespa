// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.searchdefinition.document.TypedKey;

import java.io.Serializable;
import java.util.*;

import static com.yahoo.text.Lowercase.toLowerCase;

/**
 * A summary field
 *
 * @author bratseth
 */
public class SummaryField extends Field implements Cloneable, TypedKey {

    /**
     * This class represents a source (field name).
     */
    public static class Source implements Serializable {
        private String name;
        private boolean override = false;
        public Source(String name) {
            this.name = name;
        }
        public String getName() { return name; }
        public void setOverride(boolean override) { this.override = override; }
        public boolean getOverride() { return override; }
        public int hashCode() {
            return name.hashCode() + Boolean.valueOf(override).hashCode();
        }
        public boolean equals(Object obj) {
            if (!(obj instanceof Source)) {
                return false;
            }
            Source other = (Source)obj;
            return name.equals(other.name) &&
                    override == other.override;
        }
        public String toString() {
            return name;
        }
    }

    /** A name-value property (used for smart summary) */
    public static class Property implements Serializable {
        private String name;
        private String value;
        public Property(String name, String value) {
            this.name = name;
            this.value = value;
        }
        public String getName() { return name; }
        public String getValue() { return value; }
        @Override
        public int hashCode() {
            return name.hashCode() + 17*value.hashCode();
        }
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Property)) {
                return false;
            }
            Property other = (Property)obj;
            return name.equals(other.name) && value.equals(other.value);
        }
    }

    /** The transform to perform on the stored source */
    private SummaryTransform transform=SummaryTransform.NONE;

    /** The command used per field in vsmsummary */
    private VsmCommand vsmCommand = VsmCommand.NONE;

    /**
     * The data sources for this output summary field, in prioritized order
     * (use only second source if first yields no result after transformation
     * and so on). If no sources are given explicitly, the field of the same
     * name as this summary field is used
     */
    private Set<Source> sources = new java.util.LinkedHashSet<>();

    private Set<String> destinations=new java.util.LinkedHashSet<>();

    /** True if this field was defined implicitly */
    private boolean implicit=false;

    /** The list of properties for this summary field */
    private List<Property> properties = new ArrayList<>();

    /** Creates a summary field with NONE as transform */
    public SummaryField(String name, DataType type) {
        this(name,type, SummaryTransform.NONE);
    }

    /** Creates a summary field with NONE as transform */
    public SummaryField(Field field) {
        this(field, SummaryTransform.NONE);
    }


    public SummaryField(Field field, SummaryTransform transform) {
        this(field.getName(), field.getDataType(), transform);
    }

    public SummaryField(String name, DataType type, SummaryTransform transform) {
        super(name, type);
        this.transform=transform;
    }

    public void setImplicit(boolean implicit) { this.implicit=implicit; }

    @Override // override to make public
    @Deprecated
    public void setDataType(DataType type) {
        super.setDataType(type);
    }

    public boolean isImplicit() { return implicit; }

    public void setTransform(SummaryTransform transform) {
        this.transform=transform;
        if (SummaryTransform.DYNAMICTEASER.equals(transform) || SummaryTransform.BOLDED.equals(transform)) {
            // This is the kind of logic we want to have in processing,
            // but can't because of deriveDocuments mode, which doesn't run
            // processing.
            setVsmCommand(VsmCommand.FLATTENJUNIPER);
        }
    }

    public SummaryTransform getTransform() { return transform; }

    /** Returns the first source field of this, or null if the source field is not present */
    public String getSourceField() {
        String sourceName=getName();
        if (sources.size()>0)
            sourceName=sources.iterator().next().getName();
        return sourceName;
    }

    public void addSource(String name) {
        sources.add(new Source(name));
    }

    public void addSource(Source source) {
        sources.add(source);
    }

    public Iterator<Source> sourceIterator() {
        return sources.iterator();
    }

    public int getSourceCount() {
        return sources.size();
    }

    /** Returns a modifiable set of the sources of this */
    public Set<Source> getSources() { return sources; }

    /** Returns the first source name of this, or the field name if no source has been set */
    public String getSingleSource() {
        if (sources.size()==0) return getName();
        return sources.iterator().next().getName();
    }

    public void addDestination(String name) {
        destinations.add(name);
    }

    public final void addDestinations(Iterable<String> names) {
        for (String name : names) {
            addDestination(name);
        }
    }

    /** Returns an modifiable view of the destination set owned by this */
    public Set<String> getDestinations() {
        return destinations;
    }

    private String toString(Collection<?> collection) {
        StringBuilder buffer=new StringBuilder();
        for (Iterator<?> i=collection.iterator(); i.hasNext(); ) {
            buffer.append(i.next().toString());
            if (i.hasNext())
                buffer.append(", ");
        }
        return buffer.toString();
    }

    /**
     * Returns a summary field which merges the settings in the given field
     * into this field
     *
     * @param  merge the field to merge with this, if null, the merged field is
     *         <code>this</code> field
     * @throws RuntimeException if the two fields can not be merged
     */
    public SummaryField mergeWith(SummaryField merge) {
        if (merge==null) return this;
        if (this.isImplicit()) return merge;
        if (merge.isImplicit()) return this;

        if (!merge.getName().equals(getName()))
            throw new IllegalArgumentException(merge + " conflicts with " + this + ": different names");

        if (!merge.getTransform().equals(getTransform()))
            throw new IllegalArgumentException(merge + " conflicts with " + this + ": different transforms");

        if (!merge.getDataType().equals(getDataType()))
            throw new IllegalArgumentException(merge + " conflicts with " + this + ": different types");

        if (!merge.isImplicit())
            setImplicit(false);

        if (isHeadOf(this.sourceIterator(),merge.sourceIterator())) {
            // Ok
        }
        else if (isHeadOf(merge.sourceIterator(),this.sourceIterator())) {
            sources=new LinkedHashSet<>(merge.sources);
        }
        else {
            throw new IllegalArgumentException(merge + " conflicts with " + this +
                                               ": on source list must be the start of the other");
        }

        destinations.addAll(merge.destinations);

        return this;
    }

    public boolean hasSource(String name) {
        if (sources.isEmpty() && name.equals(getName())) {
            return true;
        }
        for (Source s : sources) {
            if (s.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the second list is the start of the first list
     */
    private boolean isHeadOf(Iterator<?> full, Iterator<?> head) {
        while (head.hasNext()) {
            if (!full.hasNext()) return false;

            if (!full.next().equals(head.next())) return false;
        }
        return true;
    }

    private String getDestinationString()
    {
        StringBuilder destinationString = new StringBuilder("destinations(");
        for (String destination : destinations) {
            destinationString.append(destination).append(" ");
        }
        destinationString.append(")");
        return destinationString.toString();
    }

    public String toString() {
        return
            "summary field '" + getName() + ' ' + getDestinationString() +
            "' [type: '" + getDataType().getName() +
            "' transform: '" + transform +
            "', source: '" + toString(sources) +
            "', to '" + toString(destinations) + "']";
    }

    /** Returns a string which aids locating this field in the source search definition */
    public String toLocateString() {
        return "'summary " + getName() + " type " + toLowerCase(getDataType().getName()) + "' in '" + getDestinationString() + "'";
    }

    public SummaryField clone() {
        try {
            SummaryField clone=(SummaryField)super.clone();
            if (this.sources!=null)
                clone.sources=new LinkedHashSet<>(this.sources);
            if (this.destinations!=null)
                clone.destinations=new LinkedHashSet<>(destinations);
            return clone;
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Programming error");
        }
    }

    public VsmCommand getVsmCommand() {
        return vsmCommand;
    }

    public void setVsmCommand(VsmCommand vsmCommand) {
        this.vsmCommand = vsmCommand;
    }

    /** Adds a property to this summary field */
    public void addProperty(String name, String value) {
        properties.add(new Property(name, value));
    }

    public List<Property> getProperties() {
        return properties;
    }

    /**
     * The command used when using data from this SummaryField to generate StreamingSummary config (vsmsummary).
     * Not used for ordinary Summary config.
     * 
     * @author vegardh
     *
     */
    public enum VsmCommand {
        NONE("NONE"),
        FLATTENSPACE("FLATTENSPACE"),
        FLATTENJUNIPER("FLATTENJUNIPER");

        private String cmd="";
        private VsmCommand(String cmd) {
            this.cmd=cmd;
        }
        @Override
        public String toString() {
            return cmd;
        }
    }

}
