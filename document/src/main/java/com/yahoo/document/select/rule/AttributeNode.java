// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.rule;

import com.yahoo.collections.BobHash;
import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentGet;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.FieldPath;
import com.yahoo.document.datatypes.FieldPathIteratorHandler;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.select.BucketSet;
import com.yahoo.document.select.Context;
import com.yahoo.document.select.Result;
import com.yahoo.document.select.ResultList;
import com.yahoo.document.select.Visitor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
public class AttributeNode implements ExpressionNode {

    private ExpressionNode value;
    private final List<Item> items;

    public AttributeNode(ExpressionNode value, List<Item> items) {
        this.value = value;
        this.items = new ArrayList<>(items);
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

    @Override
    public BucketSet getBucketSet(BucketIdFactory factory) {
        return null;
    }

    @Override
    public Object evaluate(Context context) {
        StringBuilder pos = new StringBuilder(value.toString());
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

            pos.append(".").append(item);
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
            if (value instanceof Number) {
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

    private static boolean looksLikeComplexFieldPath(String path) {
        for (int i = 0; i < path.length(); ++i) {
            switch (path.charAt(i)) {
                case '.':
                case '{':
                case '[':
                    return true;
            }
        }
        return false;
    }

    private static boolean isSimpleImportedField(String path, DocumentType documentType) {
        if (looksLikeComplexFieldPath(path)) {
            return false;
        }
        return documentType.hasImportedField(path);
    }

    private static Object evaluateFieldPath(String fieldPathStr, Object value) {
        if (value instanceof DocumentPut) {
            Document doc = ((DocumentPut) value).getDocument();
            if (isSimpleImportedField(fieldPathStr, doc.getDataType())) {
                // Imported fields can only be meaningfully evaluated in the backend, so we
                // explicitly treat them as if they are valid fields with missing values. This
                // will be treated the same as if it's a normal field by the selection operators.
                // This avoids any awkward interaction with Invalid values or having to
                // augment the FieldPath code with knowledge of imported fields.
                return null;
            }
            FieldPath fieldPath = doc.getDataType().buildFieldPath(fieldPathStr);
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
        } else if (value instanceof DocumentGet) {
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

    @Override
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
