// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.identifiers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @author smorgrav
 */
public abstract class Identifier {

    protected static final String strictPatternExplanation =
            "New tenant or application names must start with a letter, may contain no more than 27 " +
            "characters, and may only contain lowercase letters, digits or dashes, but no double-dashes.";
    // TODO: Use this also for instances, if they ever get proper support.
    protected static final Pattern strictPattern = Pattern.compile("^(?=.{1,27}$)[a-z](-?[a-z0-9]+)*$");
    private static final Pattern serializedIdentifierPattern = Pattern.compile("[a-zA-Z0-9_-]+");
    private static final Pattern serializedPattern = Pattern.compile("[a-zA-Z0-9_.-]+");

    private final String id;

    @JsonCreator
    public Identifier(String id) {
        Objects.requireNonNull(id, "Id string cannot be null");
        this.id = id;
        validate();
    }

    public String toDns() {
        return id.replace('_', '-');
    }

    @Override
    public String toString() {
        return id;
    }

    @JsonValue
    public String id() { return id; }

    public String capitalizedType() {
        String simpleName = this.getClass().getSimpleName();
        String suffix = "Id";
        if (simpleName.endsWith(suffix)) {
            simpleName = simpleName.substring(0, simpleName.length() - suffix.length());
        }
        return simpleName;
    }

    public void validate() {
        if (id.equals("api")) {
            throwInvalidId(id, "'api' not allowed.");
        }
    }

    protected void validateSerialized() {
        if (!serializedPattern.matcher(id).matches()) {
            throwInvalidId(id, "Must match pattern " + serializedPattern);
        }
    }

    protected void validateSerializedIdentifier() {
        if (!serializedIdentifierPattern.matcher(id).matches()) {
            throwInvalidId(id, "Must match pattern " + serializedIdentifierPattern);
        }
    }

    protected void validateNoDefault() {
        if (id.equals("default")) {
            throwInvalidId(id, "'default' not allowed.");
        }
    }

    protected void validateNoUpperCase() {
        if (!id.equals(id.toLowerCase()))
            throwInvalidId(id, "Uppercase not allowed.");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Identifier identity = (Identifier) o;

        return id.equals(identity.id);

    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public static void throwInvalidId(String id, String explanation) {
        throw new IllegalArgumentException(String.format("Invalid id: %s. %s", id, explanation));
    }

    public static void throwInvalidId(String id, String explanation, String idName) {
        throw new IllegalArgumentException(String.format("Invalid %s id: %s. %s", idName, id, explanation));
    }

}

