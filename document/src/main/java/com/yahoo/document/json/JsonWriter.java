// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
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
import com.yahoo.document.serialization.DocumentWriter;
import com.yahoo.vespa.objects.FieldBase;
import com.yahoo.vespa.objects.Serializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;

import static com.yahoo.document.json.JsonSerializationHelper.fieldNameIfNotNull;
import static com.yahoo.document.json.JsonSerializationHelper.serializeArrayField;
import static com.yahoo.document.json.JsonSerializationHelper.serializeBoolField;
import static com.yahoo.document.json.JsonSerializationHelper.serializeByte;
import static com.yahoo.document.json.JsonSerializationHelper.serializeByteArray;
import static com.yahoo.document.json.JsonSerializationHelper.serializeByteBuffer;
import static com.yahoo.document.json.JsonSerializationHelper.serializeByteField;
import static com.yahoo.document.json.JsonSerializationHelper.serializeCollectionField;
import static com.yahoo.document.json.JsonSerializationHelper.serializeDouble;
import static com.yahoo.document.json.JsonSerializationHelper.serializeDoubleField;
import static com.yahoo.document.json.JsonSerializationHelper.serializeFloat;
import static com.yahoo.document.json.JsonSerializationHelper.serializeFloatField;
import static com.yahoo.document.json.JsonSerializationHelper.serializeInt;
import static com.yahoo.document.json.JsonSerializationHelper.serializeIntField;
import static com.yahoo.document.json.JsonSerializationHelper.serializeLong;
import static com.yahoo.document.json.JsonSerializationHelper.serializeLongField;
import static com.yahoo.document.json.JsonSerializationHelper.serializeMapField;
import static com.yahoo.document.json.JsonSerializationHelper.serializePredicateField;
import static com.yahoo.document.json.JsonSerializationHelper.serializeRawField;
import static com.yahoo.document.json.JsonSerializationHelper.serializeReferenceField;
import static com.yahoo.document.json.JsonSerializationHelper.serializeShort;
import static com.yahoo.document.json.JsonSerializationHelper.serializeString;
import static com.yahoo.document.json.JsonSerializationHelper.serializeStringField;
import static com.yahoo.document.json.JsonSerializationHelper.serializeStructField;
import static com.yahoo.document.json.JsonSerializationHelper.serializeStructuredField;
import static com.yahoo.document.json.JsonSerializationHelper.serializeTensorField;
import static com.yahoo.document.json.JsonSerializationHelper.serializeWeightedSet;
import static com.yahoo.document.json.document.DocumentParser.FIELDS;
import static com.yahoo.document.json.document.DocumentParser.REMOVE;

/**
 * Serialize Document and other FieldValue instances as JSON.
 *
 * @author Steinar Knutsen
 */
public class JsonWriter implements DocumentWriter {

    private static final JsonFactory jsonFactory = new JsonFactoryBuilder()
            .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build())
            .build();

    private final JsonGenerator generator;

    private final boolean tensorShortForm;
    private final boolean tensorDirectValues;

    /**
     * Creates a JsonWriter.
     *
     * @param out the target output stream
     * @throws RuntimeException if unable to create the internal JSON generator
     */
    public JsonWriter(OutputStream out) {
        this(createPrivateGenerator(out));
    }

    public JsonWriter(OutputStream out, boolean tensorShortForm, boolean tensorDirectValues) {
        this(createPrivateGenerator(out), tensorShortForm, tensorDirectValues);
    }

    /**
     * Create a Document writer which will write to the input JSON generator.
     * JsonWriter will not close the generator and only flush it explicitly
     * after having written a full Document instance. In other words, JsonWriter
     * will <i>not</i> take ownership of the generator.
     *
     * @param generator the output JSON generator
     * @param tensorShortForm whether to use the short type-dependent form for tensor values
     * @param tensorDirectValues whether to output tensor values directly or wrapped in a map also containing the type
     */
    public JsonWriter(JsonGenerator generator, boolean tensorShortForm, boolean tensorDirectValues) {
        this.generator = generator;
        this.tensorShortForm = tensorShortForm;
        this.tensorDirectValues = tensorDirectValues;
    }

    private static JsonGenerator createPrivateGenerator(OutputStream out) {
        try {
            return jsonFactory.createGenerator(out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JsonWriter(JsonGenerator generator) {
        this(generator, false, false);
    }

    /**
     * This method will only be called if there is some type which is not
     * properly supported in the API, or if something has been changed without
     * updating this class. This implementation throws an exception if it is
     * reached.
     *
     * @throws UnsupportedOperationException if invoked
     */
    @Override
    public void write(FieldBase field, FieldValue value) {
        throw new UnsupportedOperationException("Serializing " + value.getClass().getName() + " is not supported.");
    }

    @Override
    public void write(FieldBase field, Document value) {
        try {
            fieldNameIfNotNull(generator, field);
            generator.writeStartObject();

            // this makes it impossible to refeed directly, not sure what's correct
            // perhaps just change to "put"?
            generator.writeStringField("id", value.getId().toString());

            writeFields(value);

            generator.writeEndObject();
            generator.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T extends FieldValue> void write(FieldBase field, Array<T> value) {
        serializeArrayField(this, generator, field, value);
    }

    @Override
    public <K extends FieldValue, V extends FieldValue> void write(FieldBase field, MapFieldValue<K, V> map) {
        serializeMapField(this, generator, field, map);
    }

    @Override
    public void write(FieldBase field, ByteFieldValue value) {
        serializeByteField(generator, field, value);
    }

    @Override
    public void write(FieldBase field, BoolFieldValue value) {
        serializeBoolField(generator, field, value);
    }

    @Override
    public <T extends FieldValue> void write(FieldBase field, CollectionFieldValue<T> value) {
        serializeCollectionField(this, generator, field, value);
    }

    @Override
    public void write(FieldBase field, DoubleFieldValue value) {
        serializeDoubleField(generator, field, value);
    }

    @Override
    public void write(FieldBase field, FloatFieldValue value) {
        serializeFloatField(generator, field, value);
    }

    @Override
    public void write(FieldBase field, IntegerFieldValue value) {
        serializeIntField(generator, field, value);
    }

    @Override
    public void write(FieldBase field, LongFieldValue value) {
        serializeLongField(generator, field, value);
    }

    @Override
    public void write(FieldBase field, Raw value) {
        serializeRawField(generator, field, value);
    }

    @Override
    public void write(FieldBase field, PredicateFieldValue value) {
        serializePredicateField(generator, field, value);
    }

    @Override
    public void write(FieldBase field, StringFieldValue value) {
        serializeStringField(generator, field, value);
    }

    @Override
    public void write(FieldBase field, TensorFieldValue value) {
        serializeTensorField(generator, field, value, tensorShortForm, tensorDirectValues);
    }

    @Override
    public void write(FieldBase field, ReferenceFieldValue value) {
        serializeReferenceField(generator, field, value);
    }

    @Override
    public void write(FieldBase field, Struct value) {
        serializeStructField(this, generator, field, value);
    }

    @Override
    public void write(FieldBase field, StructuredFieldValue value) {
        serializeStructuredField(this, generator, field, value);
    }

    @Override
    public <T extends FieldValue> void write(FieldBase field, WeightedSet<T> value) {
        serializeWeightedSet(generator, field, value);
    }

    @Override
    public void write(FieldBase field, AnnotationReference value) {
        // not yet implemented, it's not available in XML either
        // TODO implement
    }

    @Override
    public void write(Document document) {
        write(null, document);
    }

    @Override
    public void write(DocumentId id) {
        // NOP, fetched from Document
    }

    @Override
    public void write(DocumentType type) {
        // NOP, fetched from Document
    }

    /**
     * Utility method to easily serialize a single document.
     *
     * @param document the document to be serialized
     * @param tensorShortForm whether tensors should be serialized in a type-dependent short form
     * @param tensorDirectValues whether tensors should be serialized as direct values or wrapped in a
     *                           map also containing the type
     * @return the input document serialised as UTF-8 encoded JSON
     */
    public static byte[] toByteArray(Document document, boolean tensorShortForm, boolean tensorDirectValues) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonWriter writer = new JsonWriter(out, tensorShortForm, tensorDirectValues);
        writer.write(document);
        return out.toByteArray();
    }

    /**
     * Utility method to easily serialize a single document.
     *
     * @param document the document to be serialized
     * @return the input document serialised as UTF-8 encoded JSON
     */
    public static byte[] toByteArray(Document document) {
        // TODO Vespa 9: change tensorShortForm and tensorDirectValues default to true
        return toByteArray(document, false, false);
    }

    /**
     * Utility method to easily serialize a single document ID as a remove
     * operation.
     *
     * @param docId
     *            the document to remove or which has been removed
     * @return a document remove operation serialised as UTF-8 encoded JSON for
     *         the input document ID
     */
    public static byte[] documentRemove(DocumentId docId) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            JsonGenerator throwAway = jsonFactory.createGenerator(out);
            throwAway.writeStartObject();
            throwAway.writeStringField(REMOVE, docId.toString());
            throwAway.writeEndObject();
            throwAway.close();
        } catch (IOException e) {
            // Under normal circumstances, nothing here will be triggered
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }

    @Override
    public Serializer putByte(FieldBase field, byte value) {
        serializeByte(generator, field, value);
        return this;
    }

    @Override
    public Serializer putShort(FieldBase field, short value) {
        serializeShort(generator, field, value);
        return this;
    }

    @Override
    public Serializer putInt(FieldBase field, int value) {
        serializeInt(generator, field, value);
        return this;
    }

    @Override
    public Serializer putLong(FieldBase field, long value) {
        serializeLong(generator, field, value);
        return this;
    }

    @Override
    public Serializer putFloat(FieldBase field, float value) {
        serializeFloat(generator, field, value);
        return this;
    }

    @Override
    public Serializer putDouble(FieldBase field, double value) {
        serializeDouble(generator, field, value);
        return this;
    }

    @Override
    public Serializer put(FieldBase field, byte[] value) {
        serializeByteArray(generator, field, value);
        return this;
    }

    @Override
    public Serializer put(FieldBase field, ByteBuffer value) {
        serializeByteBuffer(generator, field, value);
        return this;
    }

    @Override
    public Serializer put(FieldBase field, String value) {
        serializeString(generator, field, value);
        return this;
    }

    public void writeFields(Document value) throws IOException {
        generator.writeObjectFieldStart(FIELDS);
        Iterator<Map.Entry<Field, FieldValue>> i = value.iterator();
        while (i.hasNext()) {
            Map.Entry<Field, FieldValue> entry = i.next();
            entry.getValue().serialize(entry.getKey(), this);
        }
        generator.writeEndObject();
    }

}
