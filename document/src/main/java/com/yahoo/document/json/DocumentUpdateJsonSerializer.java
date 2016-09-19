// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.annotation.AnnotationReference;
import com.yahoo.document.datatypes.Array;
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
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.serialization.DocumentUpdateWriter;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.update.AddValueUpdate;
import com.yahoo.document.update.ArithmeticValueUpdate;
import com.yahoo.document.update.AssignValueUpdate;
import com.yahoo.document.update.ClearValueUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.MapValueUpdate;
import com.yahoo.document.update.RemoveValueUpdate;
import com.yahoo.document.update.ValueUpdate;
import com.yahoo.vespa.objects.FieldBase;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import static com.yahoo.document.json.JsonSerializationHelper.*;

/**
 * @author Vegard Sjonfjell
 */
public class DocumentUpdateJsonSerializer
{
    private final JsonFactory jsonFactory = new JsonFactory();
    private final JsonDocumentUpdateWriter writer = new JsonDocumentUpdateWriter();
    private JsonGenerator generator;

    /**
     * Instantiate a DocumentUpdateJsonSerializer that outputs JSON to an OutputStream
     */
    public DocumentUpdateJsonSerializer(OutputStream outputStream) {
        wrapIOException(() -> generator = jsonFactory.createGenerator(outputStream));
    }

    /**
     * Instantiate a DocumentUpdateJsonSerializer that writes JSON using existing JsonGenerator
     */
    public DocumentUpdateJsonSerializer(JsonGenerator generator) {
        this.generator = generator;
    }

    /**
     * Serialize a DocumentUpdate tree to JSON
     */
    public void serialize(DocumentUpdate update) {
        writer.write(update);
    }

    private class JsonDocumentUpdateWriter implements DocumentUpdateWriter, FieldWriter {
        @Override
        public void write(DocumentUpdate update) {
            wrapIOException(() -> {
                generator.writeStartObject();
                generator.writeStringField("update", update.getId().toString());

                if (update.getCondition().isPresent()) {
                    generator.writeStringField("condition", update.getCondition().getSelection());
                }

                generator.writeObjectFieldStart("fields");
                for (FieldUpdate up : update.getFieldUpdates()) {
                    up.serialize(this);
                }
                generator.writeEndObject();

                generator.writeEndObject();
                generator.flush();
            });
        }

        @Override
        public void write(FieldUpdate fieldUpdate) {
            wrapIOException(() -> {
                generator.writeObjectFieldStart(fieldUpdate.getField().getName());

                ArrayList<ValueUpdate> removeValueUpdates = new ArrayList<>();
                ArrayList<ValueUpdate> addValueUpdates = new ArrayList<>();

                final DataType dataType = fieldUpdate.getField().getDataType();
                for (ValueUpdate valueUpdate : fieldUpdate.getValueUpdates()) {
                    if (valueUpdate instanceof RemoveValueUpdate) {
                        removeValueUpdates.add(valueUpdate);
                    }
                    else if (valueUpdate instanceof AddValueUpdate) {
                        addValueUpdates.add(valueUpdate);
                    }
                    else {
                        valueUpdate.serialize(this, dataType);
                    }
                }

                writeAddOrRemoveValueUpdates("remove", removeValueUpdates, dataType);
                writeAddOrRemoveValueUpdates("add", addValueUpdates, dataType);

                generator.writeEndObject();
            });
        }

        private void writeAddOrRemoveValueUpdates(String arrayFieldName, ArrayList<ValueUpdate> valueUpdates, DataType dataType) throws IOException {
            if (!valueUpdates.isEmpty()) {
                generator.writeArrayFieldStart(arrayFieldName);
                for (ValueUpdate valueUpdate : valueUpdates) {
                    valueUpdate.serialize(this, dataType);
                }
                generator.writeEndArray();
            }
        }

        @Override
        public void write(AddValueUpdate update, DataType superType) {
            update.getValue().serialize(this);
        }

        /* This is the 'match' operation */
        @Override
        public void write(MapValueUpdate update, DataType superType) {
            wrapIOException(() -> {
                generator.writeObjectFieldStart("match");
                generator.writeFieldName("element");
                update.getValue().serialize(null, this);
                update.getUpdate().serialize(this, superType);
                generator.writeEndObject();
            });
        }

        @Override
        public void write(ArithmeticValueUpdate update) {
            ArithmeticValueUpdate.Operator operator = update.getOperator();
            String operationKey;

            switch (operator) {
                case ADD:
                    operationKey = "increment";
                    break;
                case DIV:
                    operationKey = "divide";
                    break;
                case MUL:
                    operationKey = "multiply";
                    break;
                case SUB:
                    operationKey = "decrement";
                    break;
                default:
                    throw new RuntimeException(String.format("Unrecognized arithmetic operator '%s'", operator.name));
            }

            wrapIOException(() -> generator.writeFieldName(operationKey));
            update.getValue().serialize(this);
        }

        @Override
        public void write(AssignValueUpdate update, DataType superType) {
            wrapIOException(() -> generator.writeFieldName("assign"));
            update.getValue().serialize(null, this);
        }

        @Override
        public void write(RemoveValueUpdate update, DataType superType) {
            update.getValue().serialize(null, this);
        }

        @Override
        public void write(ClearValueUpdate clearValueUpdate, DataType superType) {
            throw new JsonSerializationException("Serialization of ClearValueUpdate is not supported (use AssignValueUpdate with value null instead)");
        }

        @Override
        public void write(FieldBase field, FieldValue value) {
            throw new JsonSerializationException(String.format("Serialization of field values of type %s is not supported", value.getClass().getName()));
        }

        @Override
        public void write(FieldBase field, Document value) {
            throw new JsonSerializationException("Serialization of 'Document fields' is not supported");
        }

        @Override
        public <T extends FieldValue> void write(FieldBase field, Array<T> array) {
            serializeArrayField(this, generator, field, array);
        }

        @Override
        public <K extends FieldValue, V extends FieldValue> void write(FieldBase field, MapFieldValue<K, V> map) {
            serializeMapField(this, generator, field, map);
        }

        @Override
        public void write(FieldBase field, ByteFieldValue value) {
            serializeByte(generator, field, value.getByte());
        }

        @Override
        public <T extends FieldValue> void write(FieldBase field, CollectionFieldValue<T> value) {
            serializeCollectionField(this, generator, field, value);
        }

        @Override
        public void write(FieldBase field, DoubleFieldValue value) {
            serializeDouble(generator, field, value.getDouble());
        }

        @Override
        public void write(FieldBase field, FloatFieldValue value) {
            serializeFloat(generator, field, value.getFloat());
        }

        @Override
        public void write(FieldBase field, IntegerFieldValue value) {
            serializeInt(generator, field, value.getInteger());
        }

        @Override
        public void write(FieldBase field, LongFieldValue value) {
            serializeLong(generator, field, value.getLong());
        }

        @Override
        public void write(FieldBase field, Raw value) {
            serializeByteBuffer(generator, field, value.getByteBuffer());
        }

        @Override
        public void write(FieldBase field, PredicateFieldValue value) {
            serializePredicateField(generator, field, value);
        }

        @Override
        public void write(FieldBase field, StringFieldValue value) {
            serializeString(generator, field, value.getString());
        }

        @Override
        public void write(FieldBase field, TensorFieldValue value) {
            serializeTensorField(generator, field, value);
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
        public <T extends FieldValue> void write(FieldBase field, WeightedSet<T> weightedSet) {
            serializeWeightedSet(generator, field, weightedSet);
        }

        @Override
        public void write(FieldBase field, AnnotationReference value) {
            // Serialization of annotations are not implemented
        }
    }
}