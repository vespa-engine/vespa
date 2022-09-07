// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.MapDataType;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.document.datatypes.BoolFieldValue;
import com.yahoo.document.datatypes.ByteFieldValue;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.Float16FieldValue;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.PredicateFieldValue;
import com.yahoo.document.datatypes.Raw;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.vespa.documentmodel.SummaryTransform;

/**
 * A summary field derived from a search definition
 *
 * @author  bratseth
 */
public class SummaryClassField {

    private final String name;
    private final Type type;
    private final String command;
    private final String source;

    /** The summary field type enumeration */
    public enum Type {

        BOOL("bool"),
        BYTE("byte"),
        SHORT("short"),
        INTEGER("integer"),
        INT64("int64"),
        FLOAT16("float16"),
        FLOAT("float"),
        DOUBLE("double"),
        STRING("string"),
        DATA("data"),
        RAW("raw"),
        LONGSTRING("longstring"),
        LONGDATA("longdata"),
        XMLSTRING("xmlstring"),
        FEATUREDATA("featuredata"),
        JSONSTRING("jsonstring"),
        TENSOR("tensor");

        private final String name;

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

    public SummaryClassField(String name, DataType type, SummaryTransform transform, String source, boolean rawAsBase64) {
        this.name = name;
        this.type = convertDataType(type, transform, rawAsBase64);
        this.command = SummaryClass.getCommand(transform);
        this.source = source;
    }

    public String getName() { return name; }

    public Type getType() { return type; }

    public String getCommand() { return command; }

    public String getSource() { return source; }

    /** Converts to the right summary field type from a field datatype and a transform*/
    public static Type convertDataType(DataType fieldType, SummaryTransform transform, boolean rawAsBase64) {
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
        } else if (fval instanceof Float16FieldValue) {
            return Type.FLOAT16;
        } else if (fval instanceof FloatFieldValue) {
            return Type.FLOAT;
        } else if (fval instanceof DoubleFieldValue) {
            return Type.DOUBLE;
        } else if (fval instanceof BoolFieldValue) {
            return Type.BOOL;
        } else if (fval instanceof ByteFieldValue) {
            return Type.BYTE;
        } else if (fval instanceof Raw) {
            return rawAsBase64 ? Type.RAW : Type.DATA;
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
        } else if (fieldType instanceof NewDocumentReferenceDataType) {
            return Type.LONGSTRING;
        } else {
            throw new IllegalArgumentException("Don't know which summary type to convert " + fieldType + " to");
        }
    }

    public String toString() {
        return "summary class field " + name;
    }

}
