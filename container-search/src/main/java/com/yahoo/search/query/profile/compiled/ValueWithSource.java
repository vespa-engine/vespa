// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.compiled;

import com.yahoo.search.query.profile.DimensionValues;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Objects;
import java.util.Optional;

/**
 * A value in a query profile with information about its source.
 *
 * @author bratseth
 */
public class ValueWithSource {

    private final Object value;

    private final String source;

    private final boolean isUnoverridable;

    private final boolean isQueryProfile;

    private final QueryProfileType type;

    /** The dimension values specifying a variant in that profile, or null if it is not in a variant */
    private final DimensionValues variant;

    public ValueWithSource(Object value,
                           String source,
                           boolean isUnoverridable, boolean isQueryProfile, QueryProfileType type,
                           DimensionValues variant) {
        this.value = value;
        this.source = source;
        this.isUnoverridable = isUnoverridable;
        this.isQueryProfile = isQueryProfile;
        this.type = type;
        this.variant = variant;
    }

    /**
     * Returns the value at this key, or null if none
     * (in which case this is references a query profile which has no value set).
     */
    public Object value() { return value; }

    /** Returns the source of the query profile having a value */
    public String source() { return source; }

    /** Returns true if this value cannot be overridden in queries */
    public boolean isUnoverridable() { return isUnoverridable; }

    /**
     * Returns true if this key references a query profile (i.e a non-leaf).
     * In this case the value may or may not be null, as non-leafs may have values.
     */
    public boolean isQueryProfile() { return isQueryProfile; }

    /** Returns tye type of this if it refers to a query profile (not a leaf value), and it has a type */
    public QueryProfileType queryProfileType() { return type; }

    public ValueWithSource withValue(Object value) {
        return new ValueWithSource(value, source, isUnoverridable, isQueryProfile, type, variant);
    }

    public ValueWithSource withSource(String source) {
        return new ValueWithSource(value, source, isUnoverridable, isQueryProfile, type, variant);
    }

    public ValueWithSource withVariant(Optional<DimensionValues> variant) {
        return new ValueWithSource(value, source, isUnoverridable, isQueryProfile, type, variant.orElse(null));
    }

    /** Returns the variant having this value, or empty if it's not in a variant */
    public Optional<DimensionValues> variant() { return Optional.ofNullable(variant); }

    @Override
    public int hashCode() {
        // Value is always a value object. Don't include source in identity.
        return Objects.hash(value, isUnoverridable, type);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof ValueWithSource)) return false;

        ValueWithSource other = (ValueWithSource)o;
        if ( ! Objects.equals(this.value, other.value)) return false;
        if ( ! Objects.equals(this.isUnoverridable, other.isUnoverridable)) return false;
        if ( ! Objects.equals(this.type, other.type)) return false;
        return true;
    }

    @Override
    public String toString() {
        if (source == null && variant == null) return value.toString();

        return value + " (" +
               ( source != null ? "from query profile '" + source + "'" : "") +
               ( variant != null ? " variant " + variant : "") +
                ")";
    }

}
