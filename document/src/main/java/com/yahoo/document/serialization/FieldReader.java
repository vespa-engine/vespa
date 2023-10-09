// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 *
 */
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
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.FieldBase;


/**
 * @author <a href="mailto:ravishar@yahoo-inc.com">ravishar</a>
 *
 */
public interface FieldReader extends Deserializer {

    /**
     * Read in the value of field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    void read(FieldBase field, Document value);
    /**
     * Read in the value of field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    void read(FieldBase field, FieldValue value);

    /**
     * Read in the value of array field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    <T extends FieldValue> void read(FieldBase field, Array<T> value);

    /**
     * Read the value of a map field
     */
    <K extends FieldValue, V extends FieldValue> void read(FieldBase field, MapFieldValue<K, V> map);

    /**
     * Read in the value of byte field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    void read(FieldBase field, ByteFieldValue value);

    /**
     * Read in the value of byte field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    void read(FieldBase field, BoolFieldValue value);

    /**
     * Read in the value of collection field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    <T extends FieldValue> void read(FieldBase field, CollectionFieldValue<T> value);

    /**
     * Read in the value of double field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    void read(FieldBase field, DoubleFieldValue value);

    /**
     * Read in the value of float field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    void read(FieldBase field, FloatFieldValue value);

    /**
     * Read in the value of integer field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    void read(FieldBase field, IntegerFieldValue value);

    /**
     * Read in the value of long field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    void read(FieldBase field, LongFieldValue value);

    /**
     * Read in the value of raw field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    void read(FieldBase field, Raw value);

    /**
     * Read in the value of predicate field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    void read(FieldBase field, PredicateFieldValue value);

    /**
     * Read in the value of string field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    void read(FieldBase field, StringFieldValue value);

    /**
     * Read in the value of the given tensor field.
     *
     * @param field field description (name and data type)
     * @param value tensor field value
     */
    void read(FieldBase field, TensorFieldValue value);

    /**
     * Read in the value of the given reference field.
     *
     * @param field field description (name and data type)
     * @param value reference field value
     */
    void read(FieldBase field, ReferenceFieldValue value);

    /**
     * Read in the value of struct field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    void read(FieldBase field, Struct value);

    /**
     * Read in the value of structured field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    void read(FieldBase field, StructuredFieldValue value);


    /**
     * Read in the value of weighted set field
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    <T extends FieldValue> void read(FieldBase field, WeightedSet<T> value);

    /**
     * Read in the value of annotation reference.
     *
     * @param field - field description (name and data type)
     * @param value - field value
     */
    void read(FieldBase field, AnnotationReference value);

}
