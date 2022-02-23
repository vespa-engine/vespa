// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import com.yahoo.tensor.TensorType;


/**
 * This class holds the extracted information after parsing a type
 * declaration (typically for a field).  Since types can be complex,
 * struct names (known or unknown), or even document names, this class
 * is somewhat complicated.
 * @author arnej27959
 **/
class ParsedType {
    public enum Variant {
        NONE,
        BOOL, BYTE, INT, LONG,
        STRING,
        FLOAT, DOUBLE,
        URI, PREDICATE, TENSOR,
        ARRAY, WSET, MAP,
        DOC_REFERENCE,
        ANN_REFERENCE,
        STRUCT,
        DOCUMENT,
        UNKNOWN
    }

    private final String name;
    private final ParsedType keyType;
    private final ParsedType valType;
    private final TensorType tensorType;
    private Variant variant;
    private boolean createIfNonExistent = false;
    private boolean removeIfZero = false;

    private static Variant guessVariant(String name) {
        switch (name) {
        case "bool":      return Variant.BOOL;
        case "byte":      return Variant.BYTE;
        case "int":       return Variant.INT;
        case "long":      return Variant.LONG;
        case "string":    return Variant.STRING;
        case "float":     return Variant.FLOAT;
        case "double":    return Variant.DOUBLE;
        case "uri":       return Variant.URI;
        case "predicate": return Variant.PREDICATE;
        case "tensor":    return Variant.TENSOR;
        case "position":  return Variant.STRUCT;
        }
        if (name.startsWith("array<"))                  return Variant.ARRAY;
        if (name.startsWith("weightedset<"))            return Variant.WSET;
        if (name.startsWith("map<"))                    return Variant.MAP;
        if (name.startsWith("tensor("))                 return Variant.TENSOR;
        if (name.startsWith("struct<"))                 return Variant.STRUCT;
        if (name.startsWith("document<"))               return Variant.DOCUMENT;
        if (name.startsWith("reference<"))              return Variant.DOC_REFERENCE;
        if (name.startsWith("annotationreference<"))    return Variant.ANN_REFERENCE;
        return Variant.UNKNOWN;
    }

    public String name() { return name; }
    public Variant getVariant() { return variant; }

    private ParsedType(String name, Variant variant) {
        this(name, variant, null, null, null);
    }
    private ParsedType(String name, Variant variant, ParsedType vt) {
        this(name, variant, vt, null, null);
    }
    private ParsedType(String name, Variant variant, ParsedType kt, ParsedType vt) {
        this(name, variant, vt, kt, null);
    }
    private ParsedType(String name, Variant variant, ParsedType kt, ParsedType vt, TensorType tType) {
        this.name = name;
        this.variant = variant;
        this.keyType = kt;
        this.valType = vt;
        this.tensorType = tType;
    }

    static ParsedType mapType(ParsedType kt, ParsedType vt) {
        String name = "map<" + kt.name() + "," + vt.name() + ">";
        return new ParsedType(name, Variant.MAP, kt, vt);
    }
    static ParsedType arrayOf(ParsedType vt) {
        return new ParsedType("array<" + vt.name() + ">", Variant.ARRAY, vt);
    }
    static ParsedType wsetOf(ParsedType vt) {
        return new ParsedType("weightedset<" + vt.name() + ">", Variant.WSET, vt);
    }
    static ParsedType documentRef(ParsedType docType) {
        return new ParsedType("reference<" + docType.name + ">", Variant.DOC_REFERENCE, docType);
    }
    static ParsedType annotationRef(String name) {
        return new ParsedType("annotationreference<" + name + ">", Variant.ANN_REFERENCE);
    }
    static ParsedType tensorType(TensorType tType) {
        return new ParsedType(tType.toString(), Variant.TENSOR, null, null, tType);
    }
    static ParsedType fromName(String name) {
        return new ParsedType(name, guessVariant(name));
    }
    static ParsedType documentType(String name) {
        return new ParsedType(name, Variant.DOCUMENT);
    }

    void setCreateIfNonExistent(boolean value) {
        if (variant != Variant.WSET) {
            throw new IllegalArgumentException("CreateIfNonExistent only valid for weightedset, not "+variant);
        }
        this.createIfNonExistent = value;
    }

    void setRemoveIfZero(boolean value) {
        if (variant != Variant.WSET) {
            throw new IllegalArgumentException("RemoveIfZero only valid for weightedset, not "+variant);
        }
        this.removeIfZero = value;
    }

    void setVariant(Variant value) {
        if (variant == value) return; // already OK
        if (variant != Variant.UNKNOWN) {
            throw new IllegalArgumentException("setVariant("+value+") only valid for UNKNOWN, not: "+variant);
        }
        // maybe even more checking would be useful
        this.variant = value;
    }
}
