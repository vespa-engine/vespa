// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.yahoo.document.datatypes.*;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import org.apache.commons.codec.binary.Base64;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.annotation.AnnotationReference;
import com.yahoo.document.serialization.DocumentWriter;
import com.yahoo.vespa.objects.FieldBase;
import com.yahoo.vespa.objects.Serializer;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Serialize Document and other FieldValue instances as JSON.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class JsonWriter implements DocumentWriter {

    private static final JsonFactory jsonFactory = new JsonFactory();
    private final JsonGenerator generator;
    private final Base64 base64Encoder = new Base64();

    // I really hate exception unsafe constructors, but the alternative
    // requires generator to not be a final
    /**
     *
     * @param out
     *            the target output stream
     * @throws RuntimeException
     *             if unable to create the internal JSON generator
     */
    public JsonWriter(OutputStream out) {
        this(createPrivateGenerator(out));
    }

    private static JsonGenerator createPrivateGenerator(OutputStream out) {
        try {
            return jsonFactory.createGenerator(out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a Document writer which will write to the input JSON generator.
     * JsonWriter will not close the generator and only flush it explicitly
     * after having written a full Document instance. In other words, JsonWriter
     * will <i>not</i> take ownership of the generator.
     *
     * @param generator
     *            the output JSON generator
     */
    public JsonWriter(JsonGenerator generator) {
        this.generator = generator;
    }

    /**
     * This method will only be called if there is some type which is not
     * properly supported in the API, or if something has been changed without
     * updating this class. This implementation throws an exception if it is
     * reached.
     *
     * @throws UnsupportedOperationException
     *             if invoked
     */
    @Override
    public void write(FieldBase field, FieldValue value) {
        throw new UnsupportedOperationException("Serializing "
                + value.getClass().getName() + " is not supported.");
    }

    @Override
    public void write(FieldBase field, Document value) {
        try {
            fieldNameIfNotNull(field);
            generator.writeStartObject();
            // this makes it impossible to refeed directly, not sure what's correct
            // perhaps just change to "put"?
            generator.writeStringField("id", value.getId().toString());
            generator.writeObjectFieldStart(JsonReader.FIELDS);
            for (Iterator<Entry<Field, FieldValue>> i = value.iterator(); i
                    .hasNext();) {
                Entry<Field, FieldValue> entry = i.next();
                entry.getValue().serialize(entry.getKey(), this);
            }
            generator.writeEndObject();
            generator.writeEndObject();
            generator.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T extends FieldValue> void write(FieldBase field, Array<T> value) {
        try {
            fieldNameIfNotNull(field);
            generator.writeStartArray();
            for (Iterator<T> i = value.iterator(); i.hasNext();) {
                i.next().serialize(null, this);
            }
            generator.writeEndArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void fieldNameIfNotNull(FieldBase field) {
        if (field != null) {
            try {
                generator.writeFieldName(field.getName());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public <K extends FieldValue, V extends FieldValue> void write(
            FieldBase field, MapFieldValue<K, V> map) {
        fieldNameIfNotNull(field);
        try {
            generator.writeStartArray();
            for (Map.Entry<K, V> entry : map.entrySet()) {
                generator.writeStartObject();
                generator.writeFieldName(JsonReader.MAP_KEY);
                entry.getKey().serialize(null, this);
                generator.writeFieldName(JsonReader.MAP_VALUE);
                entry.getValue().serialize(null, this);
                generator.writeEndObject();
            }
            generator.writeEndArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void write(FieldBase field, ByteFieldValue value) {
        putByte(field, value.getByte());
    }

    @Override
    public <T extends FieldValue> void write(FieldBase field,
            CollectionFieldValue<T> value) {
        fieldNameIfNotNull(field);
        try {
            generator.writeStartArray();
            for (Iterator<T> i = value.iterator(); i.hasNext();) {
                i.next().serialize(null, this);
            }
            generator.writeEndArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void write(FieldBase field, DoubleFieldValue value) {
        putDouble(field, value.getDouble());
    }

    @Override
    public void write(FieldBase field, FloatFieldValue value) {
        putFloat(field, value.getFloat());
    }

    @Override
    public void write(FieldBase field, IntegerFieldValue value) {
        putInt(field, value.getInteger());
    }

    @Override
    public void write(FieldBase field, LongFieldValue value) {
        putLong(field, value.getLong());
    }

    @Override
    public void write(FieldBase field, Raw value) {
        put(field, value.getByteBuffer());
    }

    @Override
    public void write(FieldBase field, PredicateFieldValue value) {
        put(field, value.toString());
    }

    @Override
    public void write(FieldBase field, StringFieldValue value) {
        put(field, value.getString());
    }

    @Override
    public void write(FieldBase field, TensorFieldValue value) {
        try {
            fieldNameIfNotNull(field);
            generator.writeStartObject();
            if (value.getTensor().isPresent()) {
                Tensor tensor = value.getTensor().get();
                writeTensorDimensions(tensor.dimensions());
                writeTensorCells(tensor.cells());
            }
            generator.writeEndObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeTensorDimensions(Set<String> dimensions) throws IOException {
        generator.writeArrayFieldStart(JsonReader.TENSOR_DIMENSIONS);
        for (String dimension : dimensions) {
            generator.writeString(dimension);
        }
        generator.writeEndArray();
    }

    private void writeTensorCells(Map<TensorAddress, Double> cells) throws IOException {
        generator.writeArrayFieldStart(JsonReader.TENSOR_CELLS);
        for (Map.Entry<TensorAddress, Double> cell : cells.entrySet()) {
            generator.writeStartObject();
            writeTensorAddress(cell.getKey());
            generator.writeNumberField(JsonReader.TENSOR_VALUE, cell.getValue());
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private void writeTensorAddress(TensorAddress address) throws IOException {
        generator.writeObjectFieldStart(JsonReader.TENSOR_ADDRESS);
        for (TensorAddress.Element element : address.elements()) {
            generator.writeStringField(element.dimension(), element.label());
        }
        generator.writeEndObject();
    }

    @Override
    public void write(FieldBase field, Struct value) {
        if (value.getDataType() == PositionDataType.INSTANCE) {
            put(field, PositionDataType.renderAsString(value));
            return;
        }
        fieldNameIfNotNull(field);
        try {
            generator.writeStartObject();
            for (Iterator<Entry<Field, FieldValue>> i = value.iterator(); i
                    .hasNext();) {
                Entry<Field, FieldValue> entry = i.next();
                entry.getValue().serialize(entry.getKey(), this);
            }
            generator.writeEndObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void write(FieldBase field, StructuredFieldValue value) {
        fieldNameIfNotNull(field);
        try {
            generator.writeStartObject();
            for (Iterator<Entry<Field, FieldValue>> i = value.iterator(); i
                    .hasNext();) {
                Entry<Field, FieldValue> entry = i.next();
                entry.getValue().serialize(entry.getKey(), this);
            }
            generator.writeEndObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T extends FieldValue> void write(FieldBase field,
            WeightedSet<T> value) {
        fieldNameIfNotNull(field);
        try {
            generator.writeStartObject();
            // entrySet() is deprecated and there is no entry iterator
            for (T key : value.keySet()) {
                Integer weight = value.get(key);
                // key.toString() is according to spec
                generator.writeNumberField(key.toString(), weight);
            }
            generator.writeEndObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void write(FieldBase field, AnnotationReference value) {
        // not yet implemented, it's not available in XML either
        // TODO implement
    }

    @Override
    public Serializer putByte(FieldBase field, byte value) {
        fieldNameIfNotNull(field);
        try {
            generator.writeNumber(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public Serializer putShort(FieldBase field, short value) {
        fieldNameIfNotNull(field);
        try {
            generator.writeNumber(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public Serializer putInt(FieldBase field, int value) {
        fieldNameIfNotNull(field);
        try {
            generator.writeNumber(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public Serializer putLong(FieldBase field, long value) {
        fieldNameIfNotNull(field);
        try {
            generator.writeNumber(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public Serializer putFloat(FieldBase field, float value) {
        fieldNameIfNotNull(field);
        try {
            generator.writeNumber(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public Serializer putDouble(FieldBase field, double value) {
        fieldNameIfNotNull(field);
        try {
            generator.writeNumber(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public Serializer put(FieldBase field, byte[] value) {
        return put(field, ByteBuffer.wrap(value));
    }

    @Override
    public Serializer put(FieldBase field, ByteBuffer raw) {
        final byte[] data = new byte[raw.remaining()];
        final int origPosition = raw.position();

        fieldNameIfNotNull(field);
        // base64encoder has no encode methods with offset and
        // limit, so no use trying to get at the backing array if
        // available anyway
        raw.get(data);
        raw.position(origPosition);
        try {
            generator.writeString(base64Encoder.encodeToString(data));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public Serializer put(FieldBase field, String value) {
        if (value.length() == 0) {
            return this;
        }
        fieldNameIfNotNull(field);
        try {
            generator.writeString(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
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
     * @param document
     *            the document to be serialized
     * @return the input document serialised as UTF-8 encoded JSON
     */
    public static byte[] toByteArray(@NonNull Document document) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonWriter writer = new JsonWriter(out);
        writer.write(document);
        return out.toByteArray();
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
    public static byte[] documentRemove(@NonNull DocumentId docId) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            JsonGenerator throwAway = jsonFactory.createGenerator(out);
            throwAway.writeStartObject();
            throwAway.writeStringField(JsonReader.REMOVE, docId.toString());
            throwAway.writeEndObject();
            throwAway.close();
        } catch (IOException e) {
            // Under normal circumstances, nothing here will be triggered
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }
}
