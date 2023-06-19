// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import com.yahoo.api.annotations.Beta;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A set of fields which can be queried as one.
 *
 * @author bratseth
 */
@Beta
public class FieldSet implements FieldInfo {

    private final String name;
    private final Set<String> fieldNames;

    // Assigned when this is added to a schema
    private Schema schema = null;

    private FieldSet(Builder builder) {
        this.name = builder.name;
        this.fieldNames = Set.copyOf(builder.fieldNames);
    }

    @Override
    public String name() { return name; }

    @Override
    public Field.Type type() {
        if (schema == null || fieldNames.isEmpty()) return null;
        return randomFieldInThis().type();
    }

    /** Returns whether this field or field set is attribute(s), i.e. does indexing: attribute. */
    @Override
    public boolean isAttribute() {
        if (schema == null || fieldNames.isEmpty()) return false;
        return randomFieldInThis().isAttribute();
    }

    /** Returns whether this field is index(es), i.e. does indexing: index. */
    @Override
    public boolean isIndex() {
        if (schema == null || fieldNames.isEmpty()) return false;
        return randomFieldInThis().isIndex();
    }

    void setSchema(Schema schema) {
        if ( this.schema != null)
            throw new IllegalStateException("Cannot add field set '" + name + "' to schema '" + schema.name() +
                                            "' as it is already added to schema '" + this.schema.name() + "'");
        this.schema = schema;
    }

    /** Use a random field in this to determine its properties. Any inconsistency will have been warned about on deploy. */
    private Field randomFieldInThis() {
        return schema.fields().get(fieldNames.iterator().next());
    }

    public Set<String> fieldNames() { return fieldNames; }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof FieldSet other)) return false;
        if ( ! this.name.equals(other.name)) return false;
        if ( ! this.fieldNames.equals(other.fieldNames)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, fieldNames);
    }

    @Override
    public String toString() { return "field set '" + name + "'"; }

    public static class Builder {

        private final String name;
        private final Set<String> fieldNames = new HashSet<>();

        public Builder(String name) {
            this.name = name;
        }

        public Builder addField(String fieldName) {
            fieldNames.add(fieldName);
            return this;
        }

        public FieldSet build() {
            return new FieldSet(this);
        }

    }

}
