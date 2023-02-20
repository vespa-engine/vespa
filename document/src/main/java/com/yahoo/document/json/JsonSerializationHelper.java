// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentId;
import com.yahoo.document.Field;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.PrimitiveDataType;
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
import com.yahoo.document.internal.GeoPosType;
import com.yahoo.document.json.readers.TensorReader;
import com.yahoo.document.json.readers.TensorRemoveUpdateReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.serialization.JsonFormat;
import com.yahoo.vespa.objects.FieldBase;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author Steinar Knutsen
 * @author Vegard Sjonfjell
 */
public class JsonSerializationHelper {

    private final static Base64.Encoder base64Encoder = Base64.getEncoder(); // Important: _basic_ format

    static class JsonSerializationException extends RuntimeException {
        public JsonSerializationException(Exception base) {
            super(base);
        }

        public JsonSerializationException(String message) {
            super(message);
        }
    }

    @FunctionalInterface
    interface SubroutineThrowingIOException {
        void invoke() throws IOException;
    }

    static void wrapIOException(SubroutineThrowingIOException lambda) {
        try {
            lambda.invoke();
        } catch (IOException e) {
            throw new JsonSerializationException(e);
        }
    }

    public static void serializeTensorField(JsonGenerator generator, FieldBase field, TensorFieldValue value,
                                            boolean shortForm, boolean directValues) {
        wrapIOException(() -> {
            fieldNameIfNotNull(generator, field);
            if (value.getTensor().isPresent()) {
                Tensor tensor = value.getTensor().get();
                byte[] encoded = JsonFormat.encode(tensor, shortForm, directValues);
                generator.writeRawValue(new String(encoded, StandardCharsets.UTF_8));
            }
            else {
                generator.writeStartObject();
                generator.writeEndObject();
            }
        });
    }

    static void serializeTensorCells(JsonGenerator generator, Tensor tensor) throws IOException {
        generator.writeArrayFieldStart(TensorReader.TENSOR_CELLS);
        for (Map.Entry<TensorAddress, Double> cell : tensor.cells().entrySet()) {
            generator.writeStartObject();
            serializeTensorAddress(generator, cell.getKey(), tensor.type());
            generator.writeNumberField(TensorReader.TENSOR_VALUE, cell.getValue());
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    static void serializeTensorAddresses(JsonGenerator generator, Tensor tensor) throws IOException {
        TensorType tensorType = tensor.type();
        generator.writeArrayFieldStart(TensorRemoveUpdateReader.TENSOR_ADDRESSES);
        for (Map.Entry<TensorAddress, Double> cell : tensor.cells().entrySet()) {
            generator.writeStartObject();
            for (int i = 0; i < tensorType.dimensions().size(); i++) {
                generator.writeStringField(tensorType.dimensions().get(i).name(), cell.getKey().label(i));
            }
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private static void serializeTensorAddress(JsonGenerator generator, TensorAddress address, TensorType type) throws IOException {
        generator.writeObjectFieldStart(TensorReader.TENSOR_ADDRESS);
        for (int i = 0; i < type.dimensions().size(); i++)
            generator.writeStringField(type.dimensions().get(i).name(), address.label(i));
        generator.writeEndObject();
    }

    public static void serializeReferenceField(JsonGenerator generator, FieldBase field, ReferenceFieldValue value) {
        wrapIOException(() -> {
            fieldNameIfNotNull(generator, field);
            generator.writeString(value.getDocumentId().map(DocumentId::toString).orElse(""));
        });
    }

    public static void serializeStringField(JsonGenerator generator, FieldBase field, StringFieldValue value) {
        // Hide fields which only contains an empty string
        if (value.getString().length() == 0 && field != null) {
            return;
        }
        serializeString(generator, field, value.getString());
    }

    public static void serializeStructuredField(FieldWriter fieldWriter, JsonGenerator generator, FieldBase field, StructuredFieldValue value) {
        fieldNameIfNotNull(generator, field);

        wrapIOException(() -> {
            generator.writeStartObject();
            Iterator<Map.Entry<Field, FieldValue>> i = value.iterator();

            while (i.hasNext()) {
                Map.Entry<Field, FieldValue> entry = i.next();
                entry.getValue().serialize(entry.getKey(), fieldWriter);
            }

            generator.writeEndObject();
        });
    }

    private static void serializeGeoPos(JsonGenerator generator, FieldBase field, Struct value, GeoPosType dataType) {
        fieldNameIfNotNull(generator, field);
        wrapIOException(() -> {
                generator.writeStartObject();
                generator.writeFieldName("lat");
                generator.writeRawValue(dataType.fmtLatitude(value));
                generator.writeFieldName("lng");
                generator.writeRawValue(dataType.fmtLongitude(value));
                generator.writeEndObject();
        });
    }

    public static void serializeStructField(FieldWriter fieldWriter, JsonGenerator generator, FieldBase field, Struct value) {
        DataType dt = value.getDataType();
        if (dt instanceof GeoPosType) {
            var gpt = (GeoPosType)dt;
            if (gpt.renderJsonAsVespa8()) {
                serializeGeoPos(generator, field, value, gpt);
                return;
            }
        }
        serializeStructuredField(fieldWriter, generator, field, value);
    }

    public static <T extends FieldValue> void serializeWeightedSet(JsonGenerator generator, FieldBase field, WeightedSet<T> value) {
        // Hide empty fields
        if (value.size() == 0) {
            return;
        }
        fieldNameIfNotNull(generator, field);
        wrapIOException(() -> {
            generator.writeStartObject();

            for (T key : value.keySet()) {
                Integer weight = value.get(key);
                // key.toString() is according to spec
                generator.writeNumberField(key.toString(), weight);
            }

            generator.writeEndObject();
        });
    }

    public static <T extends FieldValue> void serializeCollectionField(FieldWriter fieldWriter, JsonGenerator generator, FieldBase field, CollectionFieldValue<T> value) {
        // Hide empty fields
        if (value.size() == 0) {
            return;
        }
        fieldNameIfNotNull(generator, field);
        wrapIOException(() -> {
            generator.writeStartArray();
            Iterator<T> i = value.iterator();

            while (i.hasNext()) {
                i.next().serialize(null, fieldWriter);
            }

            generator.writeEndArray();
        });
    }


    public static <K extends FieldValue, V extends FieldValue> void serializeMapField(FieldWriter fieldWriter, JsonGenerator generator, FieldBase field, MapFieldValue<K, V> map) {
        // Hide empty fields
        if (map.size() == 0) {
            return;
        }
        fieldNameIfNotNull(generator, field);
        wrapIOException(() -> {
            generator.writeStartObject();

            for (Map.Entry<K, V> entry : map.entrySet()) {
                K key = entry.getKey();
                DataType keyType = key.getDataType();
                if ( ! (keyType instanceof PrimitiveDataType)) {
                    throw new IllegalArgumentException("Can't use complex types as keys for map fields. Type: " + keyType);
                }
                generator.writeFieldName(key.toString());
                entry.getValue().serialize(null, fieldWriter);
            }

            generator.writeEndObject();
        });
    }

    public static <T extends FieldValue> void serializeArrayField(FieldWriter fieldWriter, JsonGenerator generator, FieldBase field, Array<T> value) {
        // Hide empty fields
        if (value.size() == 0) {
            return;
        }
        wrapIOException(() -> {
            fieldNameIfNotNull(generator, field);
            generator.writeStartArray();

            for (T elem : value) {
                elem.serialize(null, fieldWriter);
            }

            generator.writeEndArray();
        });
    }

    public static void serializeDoubleField(JsonGenerator generator, FieldBase field, DoubleFieldValue value) {
        serializeDouble(generator, field, value.getDouble());
    }

    public static void serializeFloatField(JsonGenerator generator, FieldBase field, FloatFieldValue value) {
        serializeFloat(generator, field, value.getFloat());
    }

    public static void serializeIntField(JsonGenerator generator, FieldBase field, IntegerFieldValue value) {
        serializeInt(generator, field, value.getInteger());
    }

    public static void serializeLongField(JsonGenerator generator, FieldBase field, LongFieldValue value) {
        serializeLong(generator, field, value.getLong());
    }

    public static void serializeByteField(JsonGenerator generator, FieldBase field, ByteFieldValue value) {
        serializeByte(generator, field, value.getByte());
    }

    public static void serializeBoolField(JsonGenerator generator, FieldBase field, BoolFieldValue value) {
        serializeBool(generator, field, value.getBoolean());
    }

    public static void serializePredicateField(JsonGenerator generator, FieldBase field, PredicateFieldValue value){
        serializeString(generator, field, value.toString());
    }

    public static void serializeRawField(JsonGenerator generator, FieldBase field, Raw raw) {
        serializeByteBuffer(generator, field, raw.getByteBuffer());
    }

    public static void serializeString(JsonGenerator generator, FieldBase field, String value) {
        fieldNameIfNotNull(generator, field);
        wrapIOException(() -> generator.writeString(value));
    }

    public static void serializeByte(JsonGenerator generator, FieldBase field,  byte value) {
        fieldNameIfNotNull(generator, field);
        wrapIOException(() -> generator.writeNumber(value));
    }

    public static void serializeBool(JsonGenerator generator, FieldBase field,  boolean value) {
        fieldNameIfNotNull(generator, field);
        wrapIOException(() -> generator.writeBoolean(value));
    }

    public static void serializeShort(JsonGenerator generator, FieldBase field, short value) {
        fieldNameIfNotNull(generator, field);
        wrapIOException(() -> generator.writeNumber(value));
    }

    public static void serializeInt(JsonGenerator generator, FieldBase field, int value) {
        fieldNameIfNotNull(generator, field);
        wrapIOException(() -> generator.writeNumber(value));
    }

    public static void serializeLong(JsonGenerator generator, FieldBase field, long value) {
        fieldNameIfNotNull(generator, field);
        wrapIOException(() -> generator.writeNumber(value));
    }

    public static void serializeFloat(JsonGenerator generator, FieldBase field, float value) {
        fieldNameIfNotNull(generator, field);
        wrapIOException(() -> generator.writeNumber(value));
    }

    public static void serializeDouble(JsonGenerator generator, FieldBase field, double value) {
        fieldNameIfNotNull(generator, field);
        wrapIOException(() -> generator.writeNumber(value));
    }

    public static void serializeByteBuffer(JsonGenerator generator, FieldBase field, ByteBuffer raw) {
        fieldNameIfNotNull(generator, field);

        final byte[] data = new byte[raw.remaining()];
        final int origPosition = raw.position();

        // base64encoder has no encode methods with offset and
        // limit, so no use trying to get at the backing array if
        // available anyway
        raw.get(data);
        raw.position(origPosition);

        wrapIOException(() -> generator.writeString(base64Encoder.encodeToString(data)));
    }

    public static void serializeByteArray(JsonGenerator generator, FieldBase field, byte[] value) {
        serializeByteBuffer(generator, field, ByteBuffer.wrap(value));
    }

    public static void fieldNameIfNotNull(JsonGenerator generator, FieldBase field) {
        if (field != null) {
            wrapIOException(() -> generator.writeFieldName(field.getName()));
        }
    }
}
