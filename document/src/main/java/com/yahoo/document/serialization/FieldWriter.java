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
 * Interface for writing a com.yahoo.document.datatypes.FieldValue.
 *
 * @author ravishar
 */
public interface FieldWriter extends Serializer {

    /** Writes the value of a field. */
    void write(FieldBase field, FieldValue value);

    /** Writes the value of a field. */
    void write(FieldBase field, Document value);

    /** Writes the value of an array field. */
    <T extends FieldValue> void write(FieldBase field, Array<T> value);

    /** Writes the value of a map field. */
    <K extends FieldValue, V extends FieldValue> void write(FieldBase field, MapFieldValue<K, V> map);

    /*** Writes the value of a byte field. */
    void write(FieldBase field, ByteFieldValue value);

    /** Writes the value of byte field. */
    void write(FieldBase field, BoolFieldValue value);

    /** Writes the value of a collection field. */
    <T extends FieldValue> void write(FieldBase field, CollectionFieldValue<T> value);

    /** Writes the value of a double field. */
    void write(FieldBase field, DoubleFieldValue value);

    /** Writes the value of a flosat field. */
    void write(FieldBase field, FloatFieldValue value);

    /** Writes the value of an integer collection field. */
    void write(FieldBase field, IntegerFieldValue value);

    /** Writes the value of a long field. */
    void write(FieldBase field, LongFieldValue value);

    /** Writes the value of a raw field. */
    void write(FieldBase field, Raw value);

    /** Writes the value of a predicate field. */
    void write(FieldBase field, PredicateFieldValue value);

    /** Writes the value of a string field. */
    void write(FieldBase field, StringFieldValue value);

    /** Writes the value of a tensor field. */
    void write(FieldBase field, TensorFieldValue value);

    /** Writes the value of a reference field. */
    void write(FieldBase field, ReferenceFieldValue value);

    /** Writes the value of a struct field. */
    void write(FieldBase field, Struct value);

    /** Writes the value of a structured field. */
    void write(FieldBase field, StructuredFieldValue value);

    /** Writes the value of a weighted set field. */
    <T extends FieldValue> void write(FieldBase field, WeightedSet<T> value);

    /** Writes the value of an annotation reference. */
    void write(FieldBase field, AnnotationReference value);

}
