// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.compiled;

import com.yahoo.search.query.profile.DimensionValues;

import java.util.Optional;

/**
 * A value in a query profile with information about its source.
 *
 * @author bratseth
 */
public class ValueWithSource {

    private final Object value;

    /** The source of the query profile having a value */
    private final String ownerSource;

    /** The dimension values specifying a variant in that profile, or null if it is not in a variant */
    private final DimensionValues variant;

    public ValueWithSource(Object value, String ownerSource, DimensionValues variant) {
        this.value = value;
        this.ownerSource = ownerSource;
        this.variant = variant;
    }

    public Object value() { return value; }

    public String ownerSource() { return ownerSource; }

    public ValueWithSource withValue(Object value) {
        return new ValueWithSource(value, ownerSource, variant);
    }

    /** Returns the variant having this value, or empty if it's not in a variant */
    public Optional<DimensionValues> variant() { return Optional.ofNullable(variant); }

    @Override
    public String toString() {
        return value +
               " (from query profile '" + ownerSource + "'" +
               ( variant != null ? " variant " + variant : "") +
               ")";
    }

}
