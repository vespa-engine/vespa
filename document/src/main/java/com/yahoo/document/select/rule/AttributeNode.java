// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.rule;

import com.yahoo.collections.BobHash;
import com.yahoo.document.*;
import com.yahoo.document.datatypes.FieldPathIteratorHandler;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.select.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class AttributeNode implements ExpressionNode {

    private ExpressionNode value;
    private final List<Item> items = new ArrayList<Item>();

    public AttributeNode(ExpressionNode value, List items) {
        this.value = value;
        for (Object obj : items) {
            if (obj instanceof Item) {
                this.items.add((Item)obj);
            } else {
                throw new IllegalStateException("Can not add an instance of " + obj.getClass().getName() +
                                                " as a function item.");
            }
        }
    }

    public ExpressionNode getValue() {
        return value;
    }

    public AttributeNode setValue(ExpressionNode value) {
        this.value = value;
        return this;
    }

    public List<Item> getItems() {
        return items;
    }

    // Inherit doc from ExpressionNode.
    public BucketSet getBucketSet(BucketIdFactory factory) {
        return null;
    }

    // Inherit doc from ExpressionNode.
    public Object evaluate(Context context) {
        String pos = value.toString();
        Object obj = value.evaluate(context);

        StringBuilder builder = new StringBuilder();
        for (Item item : items) {
            if (obj == null) {
                throw new IllegalStateException("Can not invoke '" + item + "' on '" + pos + "' because that term " +
                                                "evaluated to null.");
            }
            if (item.getType() != Item.FUNCTION) {
                if (builder.length() > 0) {
                    builder.append(".");
                }

                builder.append(item.getName());
            } else {
                if (builder.length() > 0) {
                    obj = evaluateFieldPath(builder.toString(), obj);
                    builder = new StringBuilder();
                }

                obj = evaluateFunction(item.getName(), obj);
            }

            pos = pos + "." + item;
        }

        if (builder.length() > 0) {
            obj = evaluateFieldPath(builder.toString(), obj);
        }
        return obj;
    }

    public static class VariableValueList extends ArrayList<ResultList.VariableValue> {

    }

    static class IteratorHandler extends FieldPathIteratorHandler {
        VariableValueList values = new VariableValueList();

        @Override
        public void onPrimitive(FieldValue fv) {
            values.add(new ResultList.VariableValue((VariableMap)getVariables().clone(), fv));
        }
    }

    private static Object applyFunction(String function, Object value) {
        if (function.equalsIgnoreCase("abs")) {
            if (Number.class.isInstance(value)) {
                Number nValue = (Number)value;
                if (value instanceof Double) {
                    return nValue.doubleValue() * (nValue.doubleValue() < 0 ? -1 : 1);
                } else if (value instanceof Float) {
                    return nValue.floatValue() * (nValue.floatValue() < 0 ? -1 : 1);
                } else if (value instanceof Long) {
                    return nValue.longValue() * (nValue.longValue() < 0 ? -1 : 1);
                } else if (value instanceof Integer) {
                    return nValue.intValue() * (nValue.intValue() < 0 ? -1 : 1);
                }
            }
            throw new IllegalStateException("Function 'abs' is only available for numerical values.");
        } else if (function.equalsIgnoreCase("hash")) {
            return BobHash.hash(value.toString());
        } else if (function.equalsIgnoreCase("lowercase")) {
            return value.toString().toLowerCase();
        } else if (function.equalsIgnoreCase("uppercase")) {
            return value.toString().toUpperCase();
        }
        throw new IllegalStateException("Function '" + function + "' is not supported.");
    }

    private static Object evaluateFieldPath(String fieldPth, Object value) {
        if (value instanceof DocumentPut) {
            final Document doc = ((DocumentPut) value).getDocument();
            FieldPath fieldPath = doc.getDataType().buildFieldPath(fieldPth);
            IteratorHandler handler = new IteratorHandler();
            doc.iterateNested(fieldPath, 0, handler);
            if (handler.values.isEmpty()) {
                return null;
            }
            return handler.values;
        } else if (value instanceof DocumentUpdate) {
            return Result.INVALID;
        } else if (value instanceof DocumentRemove) {
            return Result.INVALID;
        }
        return Result.FALSE;
    }

    private static Object evaluateFunction(String function, Object value) {
        if (value instanceof VariableValueList) {
            VariableValueList retVal = new VariableValueList();

            for (ResultList.VariableValue val : ((VariableValueList)value)) {
                retVal.add(new ResultList.VariableValue(
                        (FieldPathIteratorHandler.VariableMap)val.getVariables().clone(),
                        applyFunction(function, val.getValue())));
            }

            return retVal;
        }

        return applyFunction(function, value);
    }

    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append(value);
        for (Item item : items) {
            ret.append(".").append(item);
        }
        return ret.toString();
    }

    public OrderingSpecification getOrdering(int order) {
        return null;
    }

    public static class Item {
        public static final int ATTRIBUTE = 0;
        public static final int FUNCTION = 1;

        private String name;
        private int type = ATTRIBUTE;

        public Item(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public Item setName(String name) {
            this.name = name;
            return this;
        }

        public int getType() {
            return type;
        }

        public Item setType(int type) {
            this.type = type;
            return this;
        }

        @Override public String toString() {
            return name + (type == FUNCTION ? "()" : "");
        }
    }
}
