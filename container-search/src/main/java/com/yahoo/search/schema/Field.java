// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A field in a schema.
 *
 * @author bratseth
 */
public class Field implements FieldInfo {

    private final String name;
    private final Type type;
    private final boolean isAttribute;
    private final boolean isIndex;
    private final List<FastMapSearchFields> fastMapSearches;
    private final Set<String> aliases;
    private final MatchType matchType;
    private final Optional<String> explanation;

    public Field(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.isAttribute = builder.isAttribute;
        this.isIndex = builder.isIndex;
        this.fastMapSearches = List.copyOf(builder.fastMapSearches);
        this.aliases = Set.copyOf(builder.aliases);
        this.matchType = builder.matchType;
        this.explanation = Optional.ofNullable(builder.explanation);
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
    public boolean hasFastMapSearch() {
        return ! fastMapSearches.isEmpty();
    }

    @Override
    public MatchType matchType() { return matchType; }

    /** Returns the free text explanation of the content and usage of this field, if any. */
    public Optional<String> explanation() { return explanation; }

    /** Returns the key and value fields of each fast map search of this field, or an empty list if it has none. */
    public List<FastMapSearchFields> fastMapSearches() {
        return fastMapSearches;
    }

    /** Returns the key and value fields of the fast map search with the given lookup name, if this field has it. */
    public Optional<FastMapSearchFields> fastMapSearch(String lookupName) {
        return fastMapSearches.stream().filter(fastMap -> fastMap.lookupName().equals(lookupName)).findFirst();
    }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof Field other)) return false;
        if ( ! this.name.equals(other.name)) return false;
        if ( ! this.type.equals(other.type)) return false;
        if ( this.isAttribute != other.isAttribute) return false;
        if ( this.isIndex != other.isIndex) return false;
        if ( ! this.fastMapSearches.equals(other.fastMapSearches)) return false;
        if ( ! this.aliases.equals(other.aliases)) return false;
        if ( ! this.matchType.equals(other.matchType)) return false;
        if ( ! this.explanation.equals(other.explanation)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, isAttribute, isIndex, fastMapSearches, aliases, matchType, explanation);
    }

    @Override
    public String toString() { return "field '" + name + "'"; }

    /** How a field is matched. */
    public enum MatchType {

        /** This is not a string field, so match type is not applicable. */
        NONE,
        /** Matched as text: Text is tokenized, and query tokens match tokens in the field. */
        TEXT,
        /** Matched as a single word: A query term matches an entire field value, or an entire element of a collection. */
        WORD,
        /** Like WORD, but without normalizing the value (except lowercasing, unless the field is cased). */
        EXACT,
        /** Matched as n-grams, such that query text can match substrings of field values. */
        GRAM

    }

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

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o == null || o.getClass() != this.getClass()) return false;
            return this.kind == ((Type)o).kind;
        }

        @Override
        public int hashCode() {
            return kind.hashCode();
        }

        @Override
        public String toString() {
            return kind.toString();
        }

        /** Creates this from a type string on the syntax following "field [name] type " in a schema definition. */
        public static Type from(String typeString) {
            if (typeString.startsWith("annotationreference<"))
                return AnnotationReferenceFieldType.from(typeString);
            if (typeString.startsWith("array<"))
                return ArrayFieldType.from(typeString);
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
                return MapFieldType.from(typeString);
            if (typeString.equals("position"))
                return new Type(Kind.POSITION);
            if (typeString.equals("predicate"))
                return new Type(Kind.PREDICATE);
            if (typeString.equals("raw"))
                return new Type(Kind.RAW);
            if (typeString.startsWith("reference<"))
                return ReferenceFieldType.from(typeString);
            if (typeString.equals("string"))
                return new Type(Kind.STRING);
            if (typeString.startsWith("tensor<") || typeString.startsWith("tensor("))
                return new TensorFieldType(TensorType.fromSpec(typeString));
            if (typeString.equals("url"))
                return new Type(Kind.URL);
            if (typeString.startsWith("weightedset<"))
                return WeightedSetFieldType.from(typeString);
            else
                return new StructFieldType(typeString);
        }

    }

    public static class TensorFieldType extends Type {

        private final TensorType tensorType;

        public TensorFieldType(TensorType tensorType) {
            super(Kind.TENSOR);
            this.tensorType = tensorType;
        }

        public TensorType tensorType() { return tensorType; }

        @Override
        public boolean equals(Object o) {
            if ( ! super.equals(o)) return false;
            return this.tensorType.equals(((TensorFieldType)o).tensorType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), tensorType);
        }

        @Override
        public String toString() {
            return tensorType.toString();
        }

    }

    public static class MapFieldType extends Type {

        private final Type keyType;
        private final Type valueType;

        public MapFieldType(Type keyType, Type valueType) {
            super(Kind.MAP);
            this.keyType = keyType;
            this.valueType = valueType;
        }

        public Type keyType() {
            return keyType;
        }

        public Type valueType() {
            return valueType;
        }

        @Override
        public boolean equals(Object o) {
            if ( ! super.equals(o)) return false;
            var other = (MapFieldType)o;
            return this.keyType.equals(other.keyType) && this.valueType.equals(other.valueType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), keyType, valueType);
        }

        public static Type from(String typeString) {
            typeString = typeString.substring("map<".length());
            typeString = typeString.substring(0, typeString.lastIndexOf(">"));
            // we can split on first comma, since key can only be primitive type.
            String[] parts = Arrays.stream(typeString.split(",", 2)).map(String::trim).toArray(String[]::new);
            return new MapFieldType(Type.from(parts[0]), Type.from(parts[1]));
        }
    }

    public static class ArrayFieldType extends Type {

        private final Type elementType;

        public ArrayFieldType(Type elementType) {
            super(Kind.ARRAY);
            this.elementType = elementType;
        }

        public Type elementType() { return elementType; }

        @Override
        public boolean equals(Object o) {
            if ( ! super.equals(o)) return false;
            return this.elementType.equals(((ArrayFieldType)o).elementType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), elementType);
        }

        public static Type from(String typeString) {
            return new ArrayFieldType(Type.from(typeArgument("array<", typeString)));
        }

    }

    public static class WeightedSetFieldType extends Type {

        private final Type elementType;

        public WeightedSetFieldType(Type elementType) {
            super(Kind.WEIGHTEDSET);
            this.elementType = elementType;
        }

        public Type elementType() { return elementType; }

        @Override
        public boolean equals(Object o) {
            if ( ! super.equals(o)) return false;
            return this.elementType.equals(((WeightedSetFieldType)o).elementType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), elementType);
        }

        public static Type from(String typeString) {
            return new WeightedSetFieldType(Type.from(typeArgument("weightedset<", typeString)));
        }

    }

    public static class ReferenceFieldType extends Type {

        private final String documentType;

        public ReferenceFieldType(String documentType) {
            super(Kind.REFERENCE);
            this.documentType = Objects.requireNonNull(documentType);
        }

        /** Returns the name of the document type referenced. */
        public String documentType() { return documentType; }

        @Override
        public boolean equals(Object o) {
            if ( ! super.equals(o)) return false;
            return this.documentType.equals(((ReferenceFieldType)o).documentType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), documentType);
        }

        public static Type from(String typeString) {
            return new ReferenceFieldType(typeArgument("reference<", typeString));
        }

    }

    public static class StructFieldType extends Type {

        private final String name;

        public StructFieldType(String name) {
            super(Kind.STRUCT);
            this.name = Objects.requireNonNull(name);
        }

        /** Returns the name of the struct type. */
        public String name() { return name; }

        @Override
        public boolean equals(Object o) {
            if ( ! super.equals(o)) return false;
            return this.name.equals(((StructFieldType)o).name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), name);
        }

    }

    public static class AnnotationReferenceFieldType extends Type {

        private final String annotationType;

        public AnnotationReferenceFieldType(String annotationType) {
            super(Kind.ANNOTATIONREFERENCE);
            this.annotationType = Objects.requireNonNull(annotationType);
        }

        /** Returns the name of the annotation type referenced. */
        public String annotationType() { return annotationType; }

        @Override
        public boolean equals(Object o) {
            if ( ! super.equals(o)) return false;
            return this.annotationType.equals(((AnnotationReferenceFieldType)o).annotationType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), annotationType);
        }

        public static Type from(String typeString) {
            return new AnnotationReferenceFieldType(typeArgument("annotationreference<", typeString));
        }

    }

    /** Returns the type argument of a type string on the form prefix + argument + ">", such as {@code array<string>}. */
    private static String typeArgument(String prefix, String typeString) {
        return typeString.substring(prefix.length(), typeString.lastIndexOf(">")).trim();
    }

    /**
     * The name of the lookup field, and the struct fields holding the key and the value, of a field with
     * fast map search, which is either a map, or an array of a struct acting as a map entry.
     */
    public record FastMapSearchFields(String lookupName, String keyField, Type keyType, String valueField, Type valueType) {

        public FastMapSearchFields {
            Objects.requireNonNull(lookupName);
            Objects.requireNonNull(keyField);
            Objects.requireNonNull(keyType);
            Objects.requireNonNull(valueField);
            Objects.requireNonNull(valueType);
        }

        /** Returns the fast map search fields of a map, whose key and value are always named key and value. */
        public static FastMapSearchFields of(String lookupName, MapFieldType mapType) {
            return new FastMapSearchFields(lookupName, "key", mapType.keyType(), "value", mapType.valueType());
        }

    }

    public static class Builder {

        private final String name;
        private final Type type;
        private final Set<String> aliases = new HashSet<>();
        private boolean isAttribute;
        private boolean isIndex;
        private final List<FastMapSearchFields> fastMapSearches = new ArrayList<>();
        private String explanation = null;
        private MatchType matchType = MatchType.NONE;

        public Builder(String name, String typeString) {
            this.name = name;
            this.type = Type.from(typeString);
        }

        /** Sets a free text explanation of the content and usage of this field. */
        public Builder setExplanation(String explanation) {
            this.explanation = Objects.requireNonNull(explanation);
            return this;
        }

        /** Sets how this field is matched. This should be NONE (the default) for fields which are not strings. */
        public Builder setMatchType(MatchType matchType) {
            this.matchType = Objects.requireNonNull(matchType);
            return this;
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

        /** Adds a fast map search with the given lookup field name to this field, which must be a map. */
        public Builder addFastMapSearch(String lookupName) {
            if (type instanceof MapFieldType mapType) {
                return addFastMapSearch(FastMapSearchFields.of(lookupName, mapType));
            }
            throw new IllegalArgumentException("Fast map search on " + name + " requires the key and value " +
                                               "fields, since it is not a map but " + type);
        }

        /** Adds a fast map search to this field. A field may have several, with different lookup names. */
        public Builder addFastMapSearch(FastMapSearchFields fastMapSearch) {
            Objects.requireNonNull(fastMapSearch);
            if (fastMapSearches.stream().anyMatch(other -> other.lookupName().equals(fastMapSearch.lookupName()))) {
                throw new IllegalArgumentException("Field " + name + " already has a fast map search named '" +
                                                   fastMapSearch.lookupName() + "'");
            }
            this.fastMapSearches.add(fastMapSearch);
            return this;
        }

        public Field build() {
            return new Field(this);
        }

    }

}
