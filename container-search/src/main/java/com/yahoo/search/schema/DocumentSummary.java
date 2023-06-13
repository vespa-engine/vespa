// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import com.yahoo.api.annotations.Beta;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A document summary definition: Defines the schema on which a document hit may be
 * represented in a Result.
 *
 * @author bratseth
 */
@Beta
public class DocumentSummary {

    private final String name;
    private final Map<String, Field> fields;
    private final boolean dynamic;

    private DocumentSummary(Builder builder) {
        this.name = builder.name;
        this.fields = Collections.unmodifiableMap(builder.fields);
        this.dynamic = builder.dynamic;
    }

    public String name() { return name; }
    public Map<String, Field> fields() { return fields; }

    /** Returns whether this contains fields which are generated dynamically from the query and field data. */
    public boolean isDynamic() { return dynamic; }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof DocumentSummary)) return false;
        var other = (DocumentSummary)o;
        if ( ! other.name.equals(this.name)) return false;
        if ( other.dynamic != this.dynamic) return false;
        if ( ! other.fields.equals(this.fields)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, dynamic, fields);
    }

    @Override
    public String toString() {
        return "document summary '" + name + "'";
    }

    public static class Builder {

        private final String name;
        private final Map<String, Field> fields = new LinkedHashMap<>();
        private boolean dynamic;

        public Builder(String name) {
            this.name = name;
        }

        public Builder addField(String name, String type) {
            fields.put(name, new Field(name, type));
            return this;
        }

        public Builder add(Field field) {
            fields.put(field.name(), field);
            return this;
        }

        public Builder setDynamic(boolean dynamic) {
            this.dynamic = dynamic;
            return this;
        }

        public DocumentSummary build() { return new DocumentSummary(this); }

    }

    public static class Field {

        public enum Type {
            bool,
            byteType("byte"),
            shortType("short"),
            integer,
            int64,
            float16,
            floatType("float"),
            doubleType("double"),
            string,
            data,
            raw,
            longstring,
            longdata,
            jsonstring,
            featuredata,
            xmlstring,
            tensor;

            private final String name;

            Type() {
                this(null);
            }

            Type(String name) {
                this.name = name;
            }

            /** Use this, not name() to retrieve the string value of this. */
            public String asString() {
                return name != null ? name : name();
            }

            @Override
            public String toString() { return asString(); }

            public static Type fromString(String name) {
                return Arrays.stream(Type.values()).filter(t -> name.equals(t.asString())).findAny().orElseThrow();
            }

        }

        private final String name;
        private final Type type;

        public Field(String name, String type) {
            this(name, Type.fromString(type));
        }

        public Field(String name, Type type) {
            this.name = name;
            this.type = type;
        }

        public String name() { return name; }
        public Type type() { return type; }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if ( ! (o instanceof Field)) return false;
            var other = (Field)o;
            if ( ! other.name.equals(this.name)) return false;
            if ( other.type != this.type) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type);
        }

        @Override
        public String toString() {
            return "summary field '" + name + "' " + type;
        }

    }

}
