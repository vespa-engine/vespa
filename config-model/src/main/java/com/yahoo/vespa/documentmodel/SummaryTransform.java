// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    TEXTEXTRACTOR("textextractor"),
    GEOPOS("geopos"),
    ATTRIBUTECOMBINER("attributecombiner");

    private String name;

    private SummaryTransform(String name) {
        this.name=name;
    }

    public String getName() {
        return name;
    }

    /** Returns the bolded version of this transform if possible, throws if not */
    public SummaryTransform bold() {
        switch (this) {
        case NONE:
        case BOLDED:
            return BOLDED;

        case DYNAMICBOLDED:
        case DYNAMICTEASER:
            return DYNAMICBOLDED;

        default:
            throw new IllegalArgumentException("Can not bold a '" + this + "' field.");
        }
    }

    /** Returns the unbolded version of this transform */
    public SummaryTransform unbold() {
        switch (this) {
        case NONE:
        case BOLDED:
            return NONE;

        case DYNAMICBOLDED:
            return DYNAMICTEASER;

        default:
            return this;
        }
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
        switch (this) {
        case ATTRIBUTE:
        case DISTANCE:
        case POSITIONS:
        case GEOPOS:
        case RANKFEATURES:
        case SUMMARYFEATURES:
        case ATTRIBUTECOMBINER:
            return true;

        default:
            return false;
        }
    }

    public String toString() {
        return name;
    }
}
