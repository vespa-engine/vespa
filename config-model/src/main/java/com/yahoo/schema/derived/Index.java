// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.NumericDataType;
import com.yahoo.document.datatypes.*;

/**
 * A type of an index structure
 *
 * @author bratseth
 */
public class Index {

    /** The index type enumeration */
    public static class Type {

        public static final Type TEXT=new Type("text");
        public static final Type INT64=new Type("long");
        public static final Type BOOLEANTREE=new Type("booleantree");

        private String name;

        private Type(String name) {
            this.name=name;
        }

        public int hashCode() {
            return name.hashCode();
        }

        public String getName() { return name; }

        public boolean equals(Object other) {
            if ( ! (other instanceof Type)) return false;
            return this.name.equals(((Type)other).name);
        }

        public String toString() {
            return "type: " + name;
        }

    }

    /** Sets the right index type from a field type */
    public static Type convertType(DataType fieldType) {
        FieldValue fval = fieldType.createFieldValue();
        if (fieldType instanceof NumericDataType) {
            return Type.INT64;
        } else if (fval instanceof StringFieldValue) {
            return Type.TEXT;
        } else if (fval instanceof Raw) {
            return Type.BOOLEANTREE;
        } else if (fval instanceof PredicateFieldValue) {
            return Type.BOOLEANTREE;
        } else if (fieldType instanceof CollectionDataType) {
            return convertType(((CollectionDataType) fieldType).getNestedType());
        } else {
            throw new IllegalArgumentException("Don't know which index type to " +
                    "convert " + fieldType + " to");
        }
    }
}
