// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

/**
 * A value class representing a search time
 * transformation on a summary field.
 *
 * @author bratseth
 */
public enum SummaryTransform {

    NONE("none"),
    ATTRIBUTE("attribute"),
    BOLDED("bolded"),
    DISTANCE("distance"),
    DYNAMICBOLDED("dynamicbolded"),
    DYNAMICTEASER("dynamicteaser"),
    POSITIONS("positions"),
    RANKFEATURES("rankfeatures"),
    SUMMARYFEATURES("summaryfeatures"),
    GEOPOS("geopos"),
    ATTRIBUTECOMBINER("attributecombiner"),
    MATCHED_ELEMENTS_FILTER("matchedelementsfilter"),
    MATCHED_ATTRIBUTE_ELEMENTS_FILTER("matchedattributeelementsfilter"),
    COPY("copy"),
    DOCUMENT_ID("documentid");

    private final String name;

    SummaryTransform(String name) {
        this.name=name;
    }

    public String getName() {
        return name;
    }

    /** Returns the bolded version of this transform if possible, throws if not */
    public SummaryTransform bold() {
        return switch (this) {
            case NONE, BOLDED -> BOLDED;
            case DYNAMICBOLDED, DYNAMICTEASER -> DYNAMICBOLDED;
            default -> throw new IllegalArgumentException("Can not bold a '" + this + "' field.");
        };
    }

    /** Returns the unbolded version of this transform */
    public SummaryTransform unbold() {
        return switch (this) {
            case NONE, BOLDED -> NONE;
            case DYNAMICBOLDED -> DYNAMICTEASER;
            default -> this;
        };
    }

    /** Returns whether this value is bolded */
    public boolean isBolded() {
        return this==BOLDED || this==DYNAMICBOLDED;
    }

    /** Whether this is dynamically generated, both teasers and bolded fields are dynamic */
    public boolean isDynamic() {
        return this==BOLDED || this==DYNAMICBOLDED || this==DYNAMICTEASER;
    }

    /** Returns whether this is a teaser, not the complete field value */
    public boolean isTeaser() {
        return this==DYNAMICBOLDED || this==DYNAMICTEASER;
    }

    /** Returns whether this transform always gets its value by accessing memory only */
    public boolean isInMemory() {
        return switch (this) {
            case ATTRIBUTE, DISTANCE, POSITIONS, GEOPOS, RANKFEATURES, SUMMARYFEATURES, ATTRIBUTECOMBINER, MATCHED_ATTRIBUTE_ELEMENTS_FILTER ->
                    true;
            default -> false;
        };
    }

    @Override
    public String toString() {
        return name;
    }

}
