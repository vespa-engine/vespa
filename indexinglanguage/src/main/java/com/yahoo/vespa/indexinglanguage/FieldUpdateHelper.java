// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.StructuredDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.update.AddValueUpdate;
import com.yahoo.document.update.ArithmeticValueUpdate;
import com.yahoo.document.update.AssignValueUpdate;
import com.yahoo.document.update.ClearValueUpdate;
import com.yahoo.document.update.MapValueUpdate;
import com.yahoo.document.update.RemoveValueUpdate;
import com.yahoo.document.update.TensorAddUpdate;
import com.yahoo.document.update.TensorModifyUpdate;
import com.yahoo.document.update.TensorRemoveUpdate;
import com.yahoo.document.update.ValueUpdate;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings("rawtypes")
public abstract class FieldUpdateHelper {

    /** Returns true if this update completely replaces the value of the field, false otherwise. */
    public static boolean isComplete(Field field, ValueUpdate update) {
        if (update instanceof AssignValueUpdate) return true;
        if (!(update instanceof MapValueUpdate)) return false;

        DataType fieldType = field.getDataType();
        if (!(fieldType instanceof StructuredDataType)) return false;

        field = ((StructuredDataType)fieldType).getField(String.valueOf(update.getValue()));
        if (field == null) return false;

        return isComplete(field, ((MapValueUpdate)update).getUpdate());
    }

    public static void applyUpdate(Field field, ValueUpdate update, Document doc) {
        doc.setFieldValue(field, applyUpdate(update, field.getDataType().createFieldValue()));
    }

    public static Document newPartialDocument(DocumentType docType, DocumentId docId, Field field, ValueUpdate update) {
        Document doc = new Document(docType, docId);
        applyUpdate(field, update, doc);
        return doc;
    }

    /** Applies the given update to the given (empty) field value. */
    @SuppressWarnings({ "unchecked" })
    private static FieldValue applyUpdate(ValueUpdate update, FieldValue value) {
        if (update instanceof ClearValueUpdate) {
            return value;
        } else if (update instanceof AssignValueUpdate) {
            value.assign(update.getValue());
            return value;
        } else if (update instanceof AddValueUpdate) {
            if (value instanceof Array) {
                ((Array)value).add(update.getValue());
            } else if (value instanceof WeightedSet) {
                ((WeightedSet)value).put(update.getValue(), ((AddValueUpdate)update).getWeight());
            }
            return value;
        } else if (update instanceof ArithmeticValueUpdate) {
            if (((ArithmeticValueUpdate)update).getOperator() == ArithmeticValueUpdate.Operator.DIV &&
                ((ArithmeticValueUpdate)update).getOperand().doubleValue() == 0) {
                throw new IllegalArgumentException("Division by zero");
            }
            value.assign(update.getValue());
            return value;
        } else if (update instanceof RemoveValueUpdate) {
            if (value instanceof Array) {
                ((Array)value).add(update.getValue());
            } else if (value instanceof WeightedSet) {
                ((WeightedSet)value).put(update.getValue(), 1);
            }
            return value;
        } else if (update instanceof MapValueUpdate) {
            if (value instanceof Array) {
                var nestedUpdate = ((MapValueUpdate)update).getUpdate();
                if (nestedUpdate instanceof AssignValueUpdate) {
                    // Can't assign an array's value type directly to the array, so we have to add it as a
                    // singular element to the partial document.
                    ((Array)value).add(nestedUpdate.getValue());
                    return value;
                } else {
                    return applyUpdate(nestedUpdate, value);
                }
            } else if (value instanceof MapFieldValue) {
                throw new UnsupportedOperationException("Can not map into a " + value.getClass().getName() + ".");
            } else if (value instanceof StructuredFieldValue) {
                Field field = ((StructuredFieldValue)value).getField(String.valueOf(update.getValue()));
                if (field == null) {
                    throw new IllegalArgumentException("Field '" + update.getValue() + "' not found");
                }
                ((StructuredFieldValue)value).setFieldValue(field, applyUpdate(((MapValueUpdate)update).getUpdate(), field.getDataType().createFieldValue()
                ));
                return value;
            } else if (value instanceof WeightedSet) {
                FieldValue weight = applyUpdate(((MapValueUpdate)update).getUpdate(), new IntegerFieldValue());
                if (!(weight instanceof IntegerFieldValue)) {
                    throw new IllegalArgumentException("Expected integer, got " + weight.getClass().getName());
                }
                ((WeightedSet)value).put(update.getValue(), ((IntegerFieldValue)weight).getInteger());
                return value;
            } else {
                throw new IllegalArgumentException("Expected multi-value data type, got " + value.getDataType().getName());
            }
        } else if (update instanceof TensorModifyUpdate) {
            return value;
        } else if (update instanceof TensorAddUpdate) {
            return value;
        } else if (update instanceof TensorRemoveUpdate) {
            return value;
        }
        throw new UnsupportedOperationException("Value update type " + update.getClass().getName() + " not supported");
    }

}
