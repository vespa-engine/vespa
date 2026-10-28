// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A set of fields which can be queried as one.
 *
 * @author bratseth
 */
public class FieldSet implements FieldInfo {

    private final String name;
    private final Set<String> fieldNames;

    // Assigned when this is added to a schema
    private Schema schema = null;

    private FieldSet(Builder builder) {
        this.name = builder.name;
        this.fieldNames = new LinkedHashSet<>(builder.fieldNames);
    }

    @Override
    public String name() { return name; }

    /** Returns the type of the fields in this, or null if it contains no fields known by the schema. */
    @Override
    public Field.Type type() {
        return representativeField().map(Field::type).orElse(null);
    }

    /** Returns whether this field or field set is attribute(s), i.e. does indexing: attribute. */
    @Override
    public boolean isAttribute() {
        return representativeField().map(Field::isAttribute).orElse(false);
    }

    /** Returns whether this field is index(es), i.e. does indexing: index. */
    @Override
    public boolean isIndex() {
        return representativeField().map(Field::isIndex).orElse(false);
    }

    @Override
    public boolean hasFastMapSearch() {
        return false;
    }

    @Override
    public Field.MatchType matchType() {
        return representativeField().map(Field::matchType).orElse(Field.MatchType.NONE);
    }

    void setSchema(Schema schema) {
        if ( this.schema != null)
            throw new IllegalStateException("Cannot add field set '" + name + "' to schema '" + schema.name() +
                                            "' as it is already added to schema '" + this.schema.name() + "'");
        this.schema = schema;
    }

    /**
     * Returns the first field in this which is known by the schema, which is used to determine the properties of this.
     * Any inconsistency between the fields will have been warned about on deploy.
     * Fields may be unknown since field sets may contain struct fields, which are not included in schemas.
     */
    Optional<Field> representativeField() {
        if (schema == null) return Optional.empty();
        return fieldNames.stream().map(name -> schema.fields().get(name)).filter(Objects::nonNull).findFirst();
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
        private final Set<String> fieldNames = new LinkedHashSet<>();

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
