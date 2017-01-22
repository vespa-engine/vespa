// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.MapDataType;
import com.yahoo.document.datatypes.*;
import com.yahoo.vespa.documentmodel.SummaryTransform;

/**
 * A summary field derived from a search definition
 *
 * @author  bratseth
 */
public class SummaryClassField {

    private final String name;

    private final Type type;

    /** The summary field type enumeration */
    public enum Type {

        BYTE("byte"),
        SHORT("short"),
        INTEGER("integer"),
        INT64("int64"),
        FLOAT("float"),
        DOUBLE("double"),
        STRING("string"),
        DATA("data"),
        LONGSTRING("longstring"),
        LONGDATA("longdata"),
        XMLSTRING("xmlstring"),
        FEATUREDATA("featuredata"),
        JSONSTRING("jsonstring"),
        TENSOR("tensor");

        private String name;

        Type(String name) {
            this.name = name;
        }

        /** Returns the name of this type */
        public String getName() {
            return name;
        }

        public String toString() {
            return "type: " + name;
        }
    }

    public SummaryClassField(String name, DataType type, SummaryTransform transform) {
        this.name = name;
        this.type = convertDataType(type, transform);
    }

    public String getName() { return name; }

    public Type getType() { return type; }

    /** Converts to the right summary field type from a field datatype and a transform*/
    public static Type convertDataType(DataType fieldType, SummaryTransform transform) {
        FieldValue fval = fieldType.createFieldValue();
        if (fval instanceof StringFieldValue) {
            if (transform != null && transform.equals(SummaryTransform.RANKFEATURES)) {
                return Type.FEATUREDATA;
            } else if (transform != null && transform.equals(SummaryTransform.SUMMARYFEATURES)) {
                return Type.FEATUREDATA;
            } else {
                return Type.LONGSTRING;
            }
        } else if (fval instanceof IntegerFieldValue) {
            return Type.INTEGER;
        } else if (fval instanceof LongFieldValue) {
            return Type.INT64;
        } else if (fval instanceof FloatFieldValue) {
            return Type.FLOAT;
        } else if (fval instanceof DoubleFieldValue) {
            return Type.DOUBLE;
        } else if (fval instanceof ByteFieldValue) {
            return Type.BYTE;
        } else if (fval instanceof Raw) {
            return Type.DATA;
        } else if (fval instanceof Struct) {
            return Type.JSONSTRING;
        } else if (fval instanceof PredicateFieldValue) {
            return Type.STRING;
        } else if (fval instanceof TensorFieldValue) {
            return Type.TENSOR;
        } else if (fieldType instanceof CollectionDataType) {
            if (transform != null && transform.equals(SummaryTransform.POSITIONS)) {
                return Type.XMLSTRING;
            } else {
                return Type.JSONSTRING;
            }
        } else if (fieldType instanceof MapDataType) {
            return Type.JSONSTRING;
        } else {
            throw new IllegalArgumentException("Don't know which summary type to " +
                    "convert " + fieldType + " to");
        }
    }

    public String toString() {
        return "summary class field " + name;
    }

}
