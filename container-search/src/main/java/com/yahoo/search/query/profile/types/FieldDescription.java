// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types;

import com.google.common.collect.ImmutableList;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.tensor.TensorType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A field description of a query profile type. Immutable.
 * Field descriptions can be sorted by name.
 *
 * @author bratseth
 */
public class FieldDescription implements Comparable<FieldDescription> {

    private final CompoundName name;
    private final FieldType type;
    private final List<String> aliases;

    /** If true, this value must be provided either in the query profile or in the search request */
    private final boolean mandatory;

    /** If true, assignments to this value from outside will be ignored */
    private final boolean overridable;

    public FieldDescription(String name, FieldType type) {
        this(name,type,false);
    }

    public FieldDescription(String name, String type) {
        this(name,FieldType.fromString(type,null));
    }

    public FieldDescription(String name, FieldType type, boolean mandatory) {
        this(name, type, mandatory, true);
    }

    public FieldDescription(String name, String type, String aliases) {
        this(name,type,aliases,false,true);
    }

    public FieldDescription(String name, FieldType type, String aliases) {
        this(name, type, aliases, false, true);
    }

    /**
     * Creates a field description
     *
     * @param name the name of the field
     * @param typeString the type of the field represented as a string - see {@link com.yahoo.search.query.profile.types.FieldType}
     * @param aliases a space-separated list of alias names of this field name. Aliases are not following dotted
     *        (meaning they are global, not that they cannot contain dots) and are case insensitive. Null is permissible
     *        if there are no aliases
     * @param mandatory whether it is mandatory to provide a value for this field. default: false
     * @param overridable whether this can be overridden when first set in a profile. Default: true
     */
    public FieldDescription(String name, String typeString, String aliases, boolean mandatory, boolean overridable) {
        this(name,FieldType.fromString(typeString,null),aliases,mandatory,overridable);
    }

    public FieldDescription(String name, FieldType type, boolean mandatory, boolean overridable) {
        this(name, type, null, mandatory, overridable);
    }

    public FieldDescription(String name, FieldType type, String aliases, boolean mandatory, boolean overridable) {
        this(new CompoundName(name), type, aliases, mandatory, overridable);
    }

    /**
     * Creates a field description from a list where the aliases are represented as a comma-separated string
     */
    public FieldDescription(CompoundName name, FieldType type, String aliases, boolean mandatory, boolean overridable) {
        this(name, type, toList(aliases), mandatory, overridable);
    }

    /**
     * Creates a field description
     *
     * @param name the name of the field
     * @param type the type of the field represented as a string - see {@link com.yahoo.search.query.profile.types.FieldType}
     * @param aliases a list of aliases, never null. Aliases are not following dotted
     *        (meaning they are global, not that they cannot contain dots) and are case insensitive.
     * @param mandatory whether it is mandatory to provide a value for this field. default: false
     * @param overridable whether this can be overridden when first set in a profile. Default: true
     */
    public FieldDescription(CompoundName name, FieldType type, List<String> aliases, boolean mandatory, boolean overridable) {
        if (name.isEmpty())
            throw new IllegalArgumentException("Illegal name ''");
        for (String nameComponent : name.asList())
            QueryProfile.validateName(nameComponent);
        this.name = name;
        this.type = type;

        // Forbidden until we can figure out the right semantics
        if (name.isCompound() && ! aliases.isEmpty()) throw new IllegalArgumentException("Aliases is not allowed with compound names");

        this.aliases = ImmutableList.copyOf(aliases);
        this.mandatory = mandatory;
        this.overridable = overridable;
    }

    private static List<String> toList(String string) {
        if (string == null || string.isEmpty()) return ImmutableList.of();
        return ImmutableList.copyOf(Arrays.asList(string.split(" ")));
    }

    /** Returns the full name of this as a string */
    public String getName() { return name.toString(); }

    /** Returns the full name of this as a compound name */
    public CompoundName getCompoundName() { return name; }

    public FieldType getType() { return type; }

    /** Returns a unmodifiable list of the aliases of this. An empty list (never null) if there are none. */
    public List<String> getAliases() { return aliases; }

    /** Returns whether this field must be provided in the query profile or the search definition. Default: false */
    public boolean isMandatory() { return mandatory; }

    /** Returns false if overrides to values for this field from the outside should be ignored. Default: true */
    public boolean isOverridable() { return overridable; }

    public int compareTo(FieldDescription other) {
        return name.toString().compareTo(other.name.toString());
    }

    /** Returns a copy of this with the name set to the argument name */
    public FieldDescription withName(CompoundName name) {
        return new FieldDescription(name, type, aliases, mandatory, overridable);
    }

    /** Returns a copy of this with the type set to the argument type */
    public FieldDescription withType(FieldType type) {
        return new FieldDescription(name, type, aliases, mandatory, overridable);
    }

    @Override
    public String toString() {
        return "field '" + name + "' type " + type.stringValue() + "" +
                (mandatory?" (mandatory)":"") + (!overridable?" (not overridable)":"");
    }

}
