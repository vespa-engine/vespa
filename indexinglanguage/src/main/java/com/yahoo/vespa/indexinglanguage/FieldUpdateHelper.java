// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.*;
import com.yahoo.document.update.*;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings("rawtypes")
public abstract class FieldUpdateHelper {

    public static boolean isComplete(Field field, ValueUpdate update) {
        if (update instanceof AssignValueUpdate) {
            return true;
        }
        if (!(update instanceof MapValueUpdate)) {
            return false;
        }
        DataType fieldType = field.getDataType();
        if (!(fieldType instanceof StructuredDataType)) {
            return false;
        }
        field = ((StructuredDataType)fieldType).getField(String.valueOf(update.getValue()));
        if (field == null) {
            return false;
        }
        return isComplete(field, ((MapValueUpdate)update).getUpdate());
    }

    public static void applyUpdate(Field field, ValueUpdate update, Document doc) {
        doc.setFieldValue(field, createFieldValue(field.getDataType().createFieldValue(), update));
    }

    public static Document newPartialDocument(DocumentType docType, DocumentId docId, Field field, ValueUpdate update) {
        Document doc = new Document(docType, docId);
        applyUpdate(field, update, doc);
        return doc;
    }

    @SuppressWarnings({ "unchecked" })
    private static FieldValue createFieldValue(FieldValue val, ValueUpdate upd) {
        if (upd instanceof ClearValueUpdate) {
            return val;
        } else if (upd instanceof AssignValueUpdate) {
            val.assign(upd.getValue());
            return val;
        } else if (upd instanceof AddValueUpdate) {
            if (val instanceof Array) {
                ((Array)val).add(upd.getValue());
            } else if (val instanceof WeightedSet) {
                ((WeightedSet)val).put(upd.getValue(), ((AddValueUpdate)upd).getWeight());
            }
            return val;
        } else if (upd instanceof ArithmeticValueUpdate) {
            if (((ArithmeticValueUpdate)upd).getOperator() == ArithmeticValueUpdate.Operator.DIV &&
                ((ArithmeticValueUpdate)upd).getOperand().doubleValue() == 0) {
                throw new IllegalArgumentException("Division by zero.");
            }
            val.assign(upd.getValue());
            return val;
        } else if (upd instanceof RemoveValueUpdate) {
            if (val instanceof Array) {
                ((Array)val).add(upd.getValue());
            } else if (val instanceof WeightedSet) {
                ((WeightedSet)val).put(upd.getValue(), 1);
            }
            return val;
        } else if (upd instanceof MapValueUpdate) {
            if (val instanceof Array) {
                return createFieldValue(val, ((MapValueUpdate)upd).getUpdate());
            } else if (val instanceof MapFieldValue) {
                throw new UnsupportedOperationException("Can not map into a " + val.getClass().getName() + ".");
            } else if (val instanceof StructuredFieldValue) {
                Field field = ((StructuredFieldValue)val).getField(String.valueOf(upd.getValue()));
                if (field == null) {
                    throw new IllegalArgumentException("Field '" + upd.getValue() + "' not found.");
                }
                ((StructuredFieldValue)val).setFieldValue(field, createFieldValue(field.getDataType().createFieldValue(),
                                                                                  ((MapValueUpdate)upd).getUpdate()));
                return val;
            } else if (val instanceof WeightedSet) {
                FieldValue weight = createFieldValue(new IntegerFieldValue(), ((MapValueUpdate)upd).getUpdate());
                if (!(weight instanceof IntegerFieldValue)) {
                    throw new IllegalArgumentException("Expected integer, got " + weight.getClass().getName() + ".");
                }
                ((WeightedSet)val).put(upd.getValue(), ((IntegerFieldValue)weight).getInteger());
                return val;
            } else {
                throw new IllegalArgumentException("Expected multi-value data type, got " + val.getDataType().getName() + ".");
            }
        }
        throw new UnsupportedOperationException("Value update type " + upd.getClass().getName() + " not supported.");
    }
}
