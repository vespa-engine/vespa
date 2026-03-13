// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import com.yahoo.tensor.TensorType;

/**
 * This class holds the extracted information after parsing a type
 * declaration (typically for a field).  Since types can be complex,
 * struct names (known or unknown), or even document names, this class
 * is somewhat complicated.
 * @author arnej27959
 **/
public class ParsedType {
    public enum Variant {
        NONE,
        BUILTIN,
        POSITION,
        TENSOR,
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

    public String toString() {
        var buf = new StringBuilder();
        buf.append("[type ").append(variant).append("] {");
        switch (variant) {
        case NONE:
            break;
        case BUILTIN:
            buf.append(name);
            break;
        case POSITION:
            buf.append(name);
            break;
        case TENSOR:
            buf.append(tensorType.toString());
            break;
        case ARRAY: buf
                .append(" array<")
                .append(valType.toString())
                .append("> ");
            break;
        case WSET: buf
                .append(" weightedset<")
                .append(valType.toString())
                .append(">");
            if (createIfNonExistent) buf.append(",createIfNonExistent");
            if (removeIfZero) buf.append(",removeIfZero");
            buf.append(" ");
            break;
        case MAP: buf
                .append(" map<")
                .append(keyType.toString())
                .append(",")
                .append(valType.toString())
                .append("> ");
            break;
        case DOC_REFERENCE: buf
                .append(" reference<")
                .append(valType.toString())
                .append("> ");
            break;
        case ANN_REFERENCE:
        case STRUCT:
        case DOCUMENT:
        case UNKNOWN:
            buf.append(" ").append(name).append(" ");
            break;
        }
        buf.append("}");
        return buf.toString();
    }

    private static Variant guessVariant(String name) {
        switch (name) {
        case "bool":      return Variant.BUILTIN;
        case "byte":      return Variant.BUILTIN;
        case "int":       return Variant.BUILTIN;
        case "long":      return Variant.BUILTIN;
        case "string":    return Variant.BUILTIN;
        case "float":     return Variant.BUILTIN;
        case "double":    return Variant.BUILTIN;
        case "uri":       return Variant.BUILTIN;
        case "predicate": return Variant.BUILTIN;
        case "raw":       return Variant.BUILTIN;
        case "tag":       return Variant.BUILTIN;
        case "position":  return Variant.POSITION;
        case "float16":   return Variant.BUILTIN;
        }
        return Variant.UNKNOWN;
    }

    public String name() { return name; }
    public Variant getVariant() { return variant; }
    public ParsedType mapKeyType() { assert(variant == Variant.MAP); return keyType; }
    public ParsedType mapValueType() { assert(variant == Variant.MAP); return valType; }
    public ParsedType nestedType() { assert(variant == Variant.ARRAY || variant == Variant.WSET); assert(valType != null); return valType; }
    public boolean getCreateIfNonExistent() { assert(variant == Variant.WSET); return this.createIfNonExistent; }
    public boolean getRemoveIfZero() { assert(variant == Variant.WSET); return this.removeIfZero; }
    public ParsedType getReferencedDocumentType() { assert(variant == Variant.DOC_REFERENCE); return valType; }
    public TensorType getTensorType() { assert(variant == Variant.TENSOR); return tensorType; }

    public String getNameOfReferencedAnnotation() {
        assert(variant == Variant.ANN_REFERENCE);
        String prefix = "annotationreference<";
        int fromPos = prefix.length();
        int toPos = name.length() - 1;
        return name.substring(fromPos, toPos);
    }

    private ParsedType(String name, Variant variant) {
        this(name, variant, null, null, null);
    }
    private ParsedType(String name, Variant variant, ParsedType vt) {
        this(name, variant, null, vt, null);
    }
    private ParsedType(String name, Variant variant, ParsedType kt, ParsedType vt) {
        this(name, variant, kt, vt, null);
    }
    private ParsedType(String name, Variant variant, ParsedType kt, ParsedType vt, TensorType tType) {
        this.name = name;
        this.variant = variant;
        this.keyType = kt;
        this.valType = vt;
        this.tensorType = tType;
    }

    public static ParsedType mapType(ParsedType kt, ParsedType vt) {
        assert(kt != null);
        assert(vt != null);
        String name = "map<" + kt.name() + "," + vt.name() + ">";
        return new ParsedType(name, Variant.MAP, kt, vt);
    }
    public static ParsedType arrayOf(ParsedType vt) {
        assert(vt != null);
        return new ParsedType("array<" + vt.name() + ">", Variant.ARRAY, vt);
    }
    public static ParsedType wsetOf(ParsedType vt) {
        assert(vt != null);
        if (vt.getVariant() != Variant.BUILTIN) {
            throw new IllegalArgumentException("weightedset of complex type '" + vt + "' is not supported");
        }
        switch (vt.name()) {
            // allowed types:
        case "byte":
        case "int":
        case "long":
        case "string":
        case "uri":
            break;
        case "bool":
            throw new IllegalArgumentException("weightedset of trivial type '" + vt + "' is not supported");
        case "predicate":
        case "raw":
        case "tag":
            throw new IllegalArgumentException("weightedset of complex type '" + vt + "' is not supported");
        case "float16":
        case "float":
        case "double":
            throw new IllegalArgumentException("weightedset of inexact type '" + vt + "' is not supported");
        default:
            throw new IllegalArgumentException("weightedset of unknown type '" + vt + "' is not supported");
        }
        return new ParsedType("weightedset<" + vt.name() + ">", Variant.WSET, vt);
    }
    public static ParsedType documentRef(ParsedType docType) {
        assert(docType != null);
        return new ParsedType("reference<" + docType.name + ">", Variant.DOC_REFERENCE, docType);
    }
    public static ParsedType annotationRef(String name) {
        return new ParsedType("annotationreference<" + name + ">", Variant.ANN_REFERENCE);
    }
    public static ParsedType tensorType(TensorType tType) {
        assert(tType != null);
        return new ParsedType(tType.toString(), Variant.TENSOR, null, null, tType);
    }
    public static ParsedType fromName(String name) {
        return new ParsedType(name, guessVariant(name));
    }
    public static ParsedType documentType(String name) {
        return new ParsedType(name, Variant.DOCUMENT);
    }

    public void setCreateIfNonExistent(boolean value) {
        if (variant != Variant.WSET) {
            throw new IllegalArgumentException("CreateIfNonExistent only valid for weightedset, not " + variant);
        }
        this.createIfNonExistent = value;
    }

    public void setRemoveIfZero(boolean value) {
        if (variant != Variant.WSET) {
            throw new IllegalArgumentException("RemoveIfZero only valid for weightedset, not " + variant);
        }
        this.removeIfZero = value;
    }

    void setVariant(Variant value) {
        if (variant == value) return; // already OK
        if (variant != Variant.UNKNOWN) {
            throw new IllegalArgumentException("setVariant(" + value + ") only valid for UNKNOWN, not: " + variant);
        }
        // maybe even more checking would be useful
        this.variant = value;
    }
}
