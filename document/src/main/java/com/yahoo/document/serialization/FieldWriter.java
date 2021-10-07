// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.Document;
import com.yahoo.document.annotation.AnnotationReference;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.BoolFieldValue;
import com.yahoo.document.datatypes.ByteFieldValue;
import com.yahoo.document.datatypes.CollectionFieldValue;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.PredicateFieldValue;
import com.yahoo.document.datatypes.Raw;
import com.yahoo.document.datatypes.ReferenceFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.vespa.objects.FieldBase;
import com.yahoo.vespa.objects.Serializer;

/**
 * Interface for writing out com.yahoo.document.datatypes.FieldValue.
 *
 * @author <a href="mailto:ravishar@yahoo-inc.com">ravishar</a>
 *
 */
public interface FieldWriter extends Serializer {

    /**
     * Write out the value of field
     *
     * @param field
     *            field description (name and data type)
     * @param value
     *            field value
     */
    void write(FieldBase field, FieldValue value);

    /**
     * Write out the value of field
     *
     * @param field
     *            field description (name and data type)
     * @param value
     *            field value
     */
    public void write(FieldBase field, Document value);

    /**
     * Write out the value of array field
     *
     * @param field
     *            field description (name and data type)
     * @param value
     *            field value
     */
    <T extends FieldValue> void write(FieldBase field, Array<T> value);

    /**
     * Write the value of a map field
     */
    <K extends FieldValue, V extends FieldValue> void write(FieldBase field,
            MapFieldValue<K, V> map);

    /**
     * Write out the value of byte field
     *
     * @param field
     *            field description (name and data type)
     * @param value
     *            field value
     */
    void write(FieldBase field, ByteFieldValue value);

    /**
     * Write out the value of byte field
     *
     * @param field
     *            field description (name and data type)
     * @param value
     *            field value
     */
    void write(FieldBase field, BoolFieldValue value);

    /**
     * Write out the value of collection field
     *
     * @param field
     *            field description (name and data type)
     * @param value
     *            field value
     */
    <T extends FieldValue> void write(FieldBase field,
            CollectionFieldValue<T> value);

    /**
     * Write out the value of double field
     *
     * @param field
     *            field description (name and data type)
     * @param value
     *            field value
     */
    void write(FieldBase field, DoubleFieldValue value);

    /**
     * Write out the value of float field
     *
     * @param field
     *            field description (name and data type)
     * @param value
     *            field value
     */
    void write(FieldBase field, FloatFieldValue value);

    /**
     * Write out the value of integer field
     *
     * @param field
     *            field description (name and data type)
     * @param value
     *            field value
     */
    void write(FieldBase field, IntegerFieldValue value);

    /**
     * Write out the value of long field
     *
     * @param field
     *            field description (name and data type)
     * @param value
     *            field value
     */
    void write(FieldBase field, LongFieldValue value);

    /**
     * Write out the value of raw field
     *
     * @param field
     *            field description (name and data type)
     * @param value
     *            field value
     */
    void write(FieldBase field, Raw value);

    /**
     * Write out the value of predicate field
     *
     * @param field
     *            field description (name and data type)
     * @param value
     *            field value
     */
    void write(FieldBase field, PredicateFieldValue value);

    /**
     * Write out the value of string field
     *
     * @param field
     *            field description (name and data type)
     * @param value
     *            field value
     */
    void write(FieldBase field, StringFieldValue value);

    /**
     * Write out the value of the given tensor field value.
     *
     * @param field field description (name and data type)
     * @param value tensor field value
     */
    void write(FieldBase field, TensorFieldValue value);

    /**
     * Write out the value of the given reference field value.
     *
     * @param field field description (name and data type)
     * @param value reference field value
     */
    void write(FieldBase field, ReferenceFieldValue value);

    /**
     * Write out the value of struct field
     *
     * @param field
     *            field description (name and data type)
     * @param value
     *            field value
     */
    void write(FieldBase field, Struct value);

    /**
     * Write out the value of structured field
     *
     * @param field
     *            field description (name and data type)
     * @param value
     *            field value
     */
    void write(FieldBase field, StructuredFieldValue value);

    /**
     * Write out the value of weighted set field
     *
     * @param field
     *            field description (name and data type)
     * @param value
     *            field value
     */
    <T extends FieldValue> void write(FieldBase field, WeightedSet<T> value);

    /**
     * Write out the value of annotation data.
     *
     * @param field
     *            field description (name and data type)
     * @param value
     *            field value
     */
    void write(FieldBase field, AnnotationReference value);
}
