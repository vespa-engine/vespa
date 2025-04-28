// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.FieldPath;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.update.AddValueUpdate;
import com.yahoo.document.update.ArithmeticValueUpdate;
import com.yahoo.document.update.AssignValueUpdate;
import com.yahoo.document.update.ClearValueUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.MapValueUpdate;
import com.yahoo.document.update.RemoveValueUpdate;
import com.yahoo.document.update.TensorAddUpdate;
import com.yahoo.document.update.TensorModifyUpdate;
import com.yahoo.document.update.TensorRemoveUpdate;
import com.yahoo.document.update.ValueUpdate;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.FieldValues;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings("rawtypes")
public class FieldUpdateFieldValues implements UpdateFieldValues {

    private final DocumentFieldValues values;
    private final Builder builder;
    private final Expression optimizedExpression;

    private FieldUpdateFieldValues(Expression optimizedExpression, DocumentFieldValues values, Builder builder) {
        this.values = values;
        this.builder = builder;
        this.optimizedExpression = optimizedExpression;
    }

    @Override
    public DocumentUpdate getOutput() {
        Document doc = values.getUpdatableOutput();
        DocumentUpdate update = new DocumentUpdate(doc.getDataType(), doc.getId());
        for (Iterator<Map.Entry<Field, FieldValue>> it = doc.iterator(); it.hasNext();) {
            Map.Entry<Field, FieldValue> entry = it.next();
            Field field = entry.getKey();
            if (field.getName().equals("sddocname")) continue;
            FieldUpdate fieldUpdate = FieldUpdate.create(field);
            fieldUpdate.addValueUpdates(builder.build(entry.getValue()));
            if (!fieldUpdate.isEmpty()) {
                update.addFieldUpdate(fieldUpdate);
            }
        }
        return update.isEmpty() ? null : update;
    }

    @Override
    public Expression getExpression(Expression expression) {
        return optimizedExpression != null ? optimizedExpression : expression;
    }

    @Override
    public DataType getFieldType(String fieldName, Expression exp) {
        return values.getFieldType(fieldName, exp);
    }

    @Override
    public FieldValue getInputValue(String fieldName) {
        return values.getInputValue(fieldName);
    }
    @Override
    public FieldValue getInputValue(FieldPath fieldPath) { return values.getInputValue(fieldPath); }

    @Override
    public FieldValues setOutputValue(String fieldName, FieldValue fieldValue, Expression exp) {
        return values.setOutputValue(fieldName, fieldValue, exp);
    }

    @Override
    public String toString() {
        return "field update values: " + values;
    }

    public static FieldUpdateFieldValues fromPartialUpdate(DocumentFieldValues documentFieldValues, ValueUpdate valueUpdate) {
        return new FieldUpdateFieldValues(null, documentFieldValues, new PartialBuilder(valueUpdate));
    }
    public static FieldUpdateFieldValues fromPartialUpdate(Expression expression, DocumentFieldValues documentFieldValues, ValueUpdate valueUpdate) {
        return new FieldUpdateFieldValues(expression, documentFieldValues, new PartialBuilder(valueUpdate));
    }

    public static FieldUpdateFieldValues fromCompleteUpdate(DocumentFieldValues documentFieldValues) {
        return new FieldUpdateFieldValues(null, documentFieldValues, new CompleteBuilder());
    }

    private interface Builder {

        List<ValueUpdate> build(FieldValue val);
    }

    private static class PartialBuilder implements Builder {

        final ValueUpdate update;

        PartialBuilder(ValueUpdate update) {
            this.update = update;
        }

        @Override
        public List<ValueUpdate> build(FieldValue val) {
            return createValueUpdates(val, update);
        }

        @SuppressWarnings({ "unchecked" })
        List<ValueUpdate> createValueUpdates(FieldValue value, ValueUpdate update) {
            List<ValueUpdate> valueUpdates = new ArrayList<>();
            if (update instanceof ClearValueUpdate) {
                valueUpdates.add(new ClearValueUpdate());
            } else if (update instanceof AssignValueUpdate) {
                valueUpdates.add(new AssignValueUpdate(value));
            } else if (update instanceof AddValueUpdate) {
                if (value instanceof Array) {
                    valueUpdates.addAll(createAddValueUpdateForArray((Array)value, ((AddValueUpdate)update).getWeight()));
                } else if (value instanceof WeightedSet) {
                    valueUpdates.addAll(createAddValueUpdateForWset((WeightedSet)value));
                } else {
                    // do nothing
                }
            } else if (update instanceof ArithmeticValueUpdate) {
                valueUpdates.add(update); // leave arithmetics alone
            } else if (update instanceof RemoveValueUpdate) {
                if (value instanceof Array) {
                    valueUpdates.addAll(createRemoveValueUpdateForEachElement(((Array)value).fieldValueIterator()));
                } else if (value instanceof WeightedSet) {
                    valueUpdates.addAll(createRemoveValueUpdateForEachElement(((WeightedSet)value).fieldValueIterator()));
                } else {
                    // do nothing
                }
            } else if (update instanceof MapValueUpdate) {
                if (value instanceof Array) {
                    valueUpdates.addAll(createMapValueUpdatesForArray((Array)value, (MapValueUpdate)update));
                } else if (value instanceof MapFieldValue) {
                    throw new UnsupportedOperationException("Can not map into a " + value.getClass().getName());
                } else if (value instanceof StructuredFieldValue) {
                    valueUpdates.addAll(createMapValueUpdatesForStruct((StructuredFieldValue)value, (MapValueUpdate)update));
                } else if (value instanceof WeightedSet) {
                    valueUpdates.addAll(createMapValueUpdatesForWset((WeightedSet)value, (MapValueUpdate)update));
                } else {
                    // do nothing
                }
            } else if (update instanceof TensorModifyUpdate) {
                valueUpdates.add(update);
            } else if (update instanceof TensorAddUpdate) {
                valueUpdates.add(update);
            } else if (update instanceof TensorRemoveUpdate) {
                valueUpdates.add(update);
            } else {
                throw new UnsupportedOperationException("Value update type " +
                                                        update.getClass().getName() + " not supported");
            }
            return valueUpdates;
        }

        @SuppressWarnings({ "unchecked" })
        private List<ValueUpdate> createAddValueUpdateForArray(Array arr, int weight) {
            List<ValueUpdate> ret = new ArrayList<>(arr.size());
            for (Iterator<FieldValue> it = arr.fieldValueIterator(); it.hasNext(); ) {
                ret.add(new AddValueUpdate(it.next(), weight));
            }
            return ret;
        }

        @SuppressWarnings({ "unchecked" })
        private List<ValueUpdate> createAddValueUpdateForWset(WeightedSet wset) {
            List<ValueUpdate> ret = new ArrayList<>(wset.size());
            for (Iterator<FieldValue> it = wset.fieldValueIterator(); it.hasNext(); ) {
                FieldValue key = it.next();
                ret.add(new AddValueUpdate(key, wset.get(key)));
            }
            return ret;
        }

        private List<ValueUpdate> createRemoveValueUpdateForEachElement(Iterator<FieldValue> it) {
            List<ValueUpdate> ret = new ArrayList<>();
            while (it.hasNext()) {
                ret.add(new RemoveValueUpdate(it.next()));
            }
            return ret;
        }

        @SuppressWarnings({ "unchecked" })
        private List<ValueUpdate> createMapValueUpdatesForArray(Array arr, MapValueUpdate upd) {
            List<ValueUpdate> ret = new ArrayList<>();
            for (Iterator<FieldValue> it = arr.fieldValueIterator(); it.hasNext();) {
                FieldValue childVal = it.next();
                for (ValueUpdate childUpd : createValueUpdates(childVal, upd.getUpdate())) {
                    // The array update is always directed towards a particular array index, which is
                    // kept as the _value_ in the original update.
                    ret.add(new MapValueUpdate(upd.getValue(), childUpd));
                }
            }
            return ret;
        }

        private List<ValueUpdate> createMapValueUpdatesForStruct(StructuredFieldValue struct, MapValueUpdate upd) {
            List<ValueUpdate> ret = new ArrayList<>();
            for (Iterator<Map.Entry<Field, FieldValue>> it = struct.iterator(); it.hasNext();) {
                Map.Entry<Field, FieldValue> entry = it.next();
                for (ValueUpdate childUpd : createValueUpdates(entry.getValue(), upd.getUpdate())) {
                    ret.add(new MapValueUpdate(new StringFieldValue(entry.getKey().getName()), childUpd));
                }
            }
            return ret;
        }

        @SuppressWarnings({ "unchecked" })
        private List<ValueUpdate> createMapValueUpdatesForWset(WeightedSet wset, MapValueUpdate upd) {
            List<ValueUpdate> ret = new ArrayList<>();
            for (Iterator<FieldValue> it = wset.fieldValueIterator(); it.hasNext();) {
                FieldValue childVal = it.next();
                for (ValueUpdate childUpd : createValueUpdates(new IntegerFieldValue(wset.get(childVal)),
                                                               upd.getUpdate()))
                {
                    ret.add(new MapValueUpdate(childVal, childUpd));
                }
            }
            return ret;
        }
    }

    private static class CompleteBuilder extends PartialBuilder {

        static final ValueUpdate nullAssign = new AssignValueUpdate(null);

        CompleteBuilder() {
            super(null);
        }

        @Override
        List<ValueUpdate> createValueUpdates(FieldValue value, ValueUpdate upd) {
            return super.createValueUpdates(value, nullAssign);
        }
    }

}
