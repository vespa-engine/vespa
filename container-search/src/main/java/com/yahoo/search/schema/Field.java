// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import com.yahoo.api.annotations.Beta;
import com.yahoo.tensor.TensorType;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A field in a schema.
 *
 * @author bratseth
 */
@Beta
public class Field implements FieldInfo {

    private final String name;
    private final Type type;
    private final boolean isAttribute;
    private final boolean isIndex;
    private final Set<String> aliases;

    public Field(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.isAttribute = builder.isAttribute;
        this.isIndex = builder.isIndex;
        this.aliases = Set.copyOf(builder.aliases);
    }

    @Override
    public String name() { return name; }

    @Override
    public Type type() { return type; }

    public Set<String> aliases() { return aliases; }

    /** Returns whether this field is an attribute, i.e. does indexing: attribute. */
    @Override
    public boolean isAttribute() { return isAttribute; }

    /** Returns whether this field is an index, i.e. does indexing: index. */
    @Override
    public boolean isIndex() { return isIndex; }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof Field other)) return false;
        if ( ! this.name.equals(other.name)) return false;
        if ( this.isAttribute != other.isAttribute) return false;
        if ( this.isIndex != other.isIndex) return false;
        if ( ! this.aliases.equals(other.aliases)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, isAttribute, isIndex, aliases);
    }

    @Override
    public String toString() { return "field '" + name + "'"; }

    public static class Type {

        private final Kind kind;

        /** The kind of type this is. */
        public enum Kind {
            ANNOTATIONREFERENCE, ARRAY, BOOL, BYTE, DOUBLE, FLOAT, INT, LONG, MAP, POSITION, PREDICATE, RAW, REFERENCE, STRING, STRUCT, TENSOR, URL, WEIGHTEDSET;
        }

        private Type(Kind kind) {
            this.kind = kind;
        }

        /**
         * Returns the kind of type this is.
         * Structured types have additional information in the subclass specific to that kind of type.
         */
        public Kind kind() { return kind; }

        /** Creates this from a type string on the syntax following "field [name] type " in a schema definition. */
        public static Type from(String typeString) {
            if (typeString.startsWith("annotationreference<"))
                return new Type(Kind.ANNOTATIONREFERENCE); // TODO: Model as subclass
            if (typeString.startsWith("array<"))
                return new Type(Kind.ARRAY); // TODO: Model as subclass
            if (typeString.equals("bool"))
                return new Type(Kind.BOOL);
            if (typeString.equals("byte"))
                return new Type(Kind.BYTE);
            if (typeString.equals("double"))
                return new Type(Kind.DOUBLE);
            if (typeString.equals("float"))
                return new Type(Kind.FLOAT);
            if (typeString.equals("int"))
                return new Type(Kind.INT);
            if (typeString.equals("long"))
                return new Type(Kind.LONG);
            if (typeString.startsWith("map<"))
                return new Type(Kind.MAP); // TODO: Model as subclass
            if (typeString.equals("position"))
                return new Type(Kind.POSITION);
            if (typeString.equals("predicate"))
                return new Type(Kind.PREDICATE);
            if (typeString.equals("raw"))
                return new Type(Kind.RAW);
            if (typeString.startsWith("reference<"))
                return new Type(Kind.REFERENCE); // TODO: Model as subclass
            if (typeString.equals("string"))
                return new Type(Kind.STRING);
            if (typeString.startsWith("tensor<") || typeString.startsWith("tensor("))
                return new TensorFieldType(TensorType.fromSpec(typeString));
            if (typeString.equals("url"))
                return new Type(Kind.URL);
            if (typeString.startsWith("weightedset<"))
                return new Type(Kind.WEIGHTEDSET); // TODO: Model as subclass
            else
                return new Type(Kind.STRUCT); // TODO: Model as a subclass
        }

    }

    public static class TensorFieldType extends Type {

        private final TensorType tensorType;

        public TensorFieldType(TensorType tensorType) {
            super(Kind.TENSOR);
            this.tensorType = tensorType;
        }

        public TensorType tensorType() { return tensorType; }

    }

    public static class Builder {

        private final String name;
        private final Type type;
        private final Set<String> aliases = new HashSet<>();
        private boolean isAttribute;
        private boolean isIndex;

        public Builder(String name, String typeString) {
            this.name = name;
            this.type = Type.from(typeString);
        }

        public Builder addAlias(String alias) {
            aliases.add(alias);
            return this;
        }

        public Builder setAttribute(boolean isAttribute) {
            this.isAttribute = isAttribute;
            return this;
        }

        public Builder setIndex(boolean isIndex) {
            this.isIndex = isIndex;
            return this;
        }

        public Field build() {
            return new Field(this);
        }

    }



}
