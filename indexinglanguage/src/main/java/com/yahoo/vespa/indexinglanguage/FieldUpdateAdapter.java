// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.vespa.indexinglanguage.expressions.FieldValueAdapter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings("rawtypes")
public class FieldUpdateAdapter implements UpdateAdapter {

    private final DocumentAdapter adapter;
    private final Builder builder;
    private final Expression optimizedExpression;

    private FieldUpdateAdapter(Expression optimizedExpression, DocumentAdapter adapter, Builder builder) {
        this.adapter = adapter;
        this.builder = builder;
        this.optimizedExpression = optimizedExpression;
    }

    @Override
    public DocumentUpdate getOutput() {
        Document doc = adapter.getUpdatableOutput();
        DocumentUpdate upd = new DocumentUpdate(doc.getDataType(), doc.getId());
        for (Iterator<Map.Entry<Field, FieldValue>> it = doc.iterator(); it.hasNext();) {
            Map.Entry<Field, FieldValue> entry = it.next();
            Field field = entry.getKey();
            if (field.getName().equals("sddocname")) {
                continue;
            }
            FieldUpdate fieldUpd = FieldUpdate.create(field);
            fieldUpd.addValueUpdates(builder.build(entry.getValue()));
            if (!fieldUpd.isEmpty()) {
                upd.addFieldUpdate(fieldUpd);
            }
        }
        return upd.isEmpty() ? null : upd;
    }

    @Override
    public Expression getExpression(Expression expression) {
        return optimizedExpression != null ? optimizedExpression : expression;
    }

    @Override
    public DataType getInputType(Expression exp, String fieldName) {
        return adapter.getInputType(exp, fieldName);
    }

    @Override
    public FieldValue getInputValue(String fieldName) {
        return adapter.getInputValue(fieldName);
    }
    @Override
    public FieldValue getInputValue(FieldPath fieldPath) { return adapter.getInputValue(fieldPath); }

    @Override
    public void tryOutputType(Expression exp, String fieldName, DataType valueType) {
        adapter.tryOutputType(exp, fieldName, valueType);
    }

    @Override
    public FieldValueAdapter setOutputValue(Expression exp, String fieldName, FieldValue fieldValue) {
        return adapter.setOutputValue(exp, fieldName, fieldValue);
    }

    public static FieldUpdateAdapter fromPartialUpdate(DocumentAdapter documentAdapter, ValueUpdate valueUpdate) {
        return new FieldUpdateAdapter(null, documentAdapter, new PartialBuilder(valueUpdate));
    }
    public static FieldUpdateAdapter fromPartialUpdate(Expression expression, DocumentAdapter documentAdapter, ValueUpdate valueUpdate) {
        return new FieldUpdateAdapter(expression, documentAdapter, new PartialBuilder(valueUpdate));
    }

    public static FieldUpdateAdapter fromCompleteUpdate(DocumentAdapter documentAdapter) {
        return new FieldUpdateAdapter(null, documentAdapter, new CompleteBuilder());
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
        List<ValueUpdate> createValueUpdates(FieldValue val, ValueUpdate upd) {
            List<ValueUpdate> lst = new ArrayList<>();
            if (upd instanceof ClearValueUpdate) {
                lst.add(new ClearValueUpdate());
            } else if (upd instanceof AssignValueUpdate) {
                lst.add(new AssignValueUpdate(val));
            } else if (upd instanceof AddValueUpdate) {
                if (val instanceof Array) {
                    lst.addAll(createAddValueUpdateForArray((Array)val, ((AddValueUpdate)upd).getWeight()));
                } else if (val instanceof WeightedSet) {
                    lst.addAll(createAddValueUpdateForWset((WeightedSet)val));
                } else {
                    // do nothing
                }
            } else if (upd instanceof ArithmeticValueUpdate) {
                lst.add(upd); // leave arithmetics alone
            } else if (upd instanceof RemoveValueUpdate) {
                if (val instanceof Array) {
                    lst.addAll(createRemoveValueUpdateForEachElement(((Array)val).fieldValueIterator()));
                } else if (val instanceof WeightedSet) {
                    lst.addAll(createRemoveValueUpdateForEachElement(((WeightedSet)val).fieldValueIterator()));
                } else {
                    // do nothing
                }
            } else if (upd instanceof MapValueUpdate) {
                if (val instanceof Array) {
                    lst.addAll(createMapValueUpdatesForArray((Array)val, (MapValueUpdate)upd));
                } else if (val instanceof MapFieldValue) {
                    throw new UnsupportedOperationException("Can not map into a " + val.getClass().getName() + ".");
                } else if (val instanceof StructuredFieldValue) {
                    lst.addAll(createMapValueUpdatesForStruct((StructuredFieldValue)val, (MapValueUpdate)upd));
                } else if (val instanceof WeightedSet) {
                    lst.addAll(createMapValueUpdatesForWset((WeightedSet)val, (MapValueUpdate)upd));
                } else {
                    // do nothing
                }
            } else if (upd instanceof TensorModifyUpdate) {
                lst.add(upd);
            } else if (upd instanceof TensorAddUpdate) {
                lst.add(upd);
            } else if (upd instanceof TensorRemoveUpdate) {
                lst.add(upd);
            } else {
                throw new UnsupportedOperationException(
                        "Value update type " + upd.getClass().getName() + " not supported.");
            }
            return lst;
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
        List<ValueUpdate> createValueUpdates(FieldValue val, ValueUpdate upd) {
            return super.createValueUpdates(val, nullAssign);
        }
    }

}
