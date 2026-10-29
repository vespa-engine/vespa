// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Executes an expression on the values found at a path inside an array of struct, a map or a struct,
 * and puts the results back in place. Other values are left as they are, so the output has the same type,
 * and the same elements in the same order, as the input.
 *
 * The path is a dot separated list of struct field names, where "key" and "value" select the key or the value
 * of a map entry. Arrays are traversed element by element without consuming any part of the path, so
 * "myvalue" selects the "myvalue" field of every struct in an array of struct, and "value.f" selects the "f" field
 * of every struct value in a map of struct.
 *
 * This is used to annotate (tokenize) selected string fields inside a complex field while keeping the field whole.
 *
 * @author arnej
 */
public final class ForEachFieldExpression extends CompositeExpression {

    private final String path;
    private final List<String> pathElements;
    private final Expression expression;

    public ForEachFieldExpression(String path, Expression expression) {
        this.path = Objects.requireNonNull(path);
        this.pathElements = List.of(path.split("\\.", -1));
        this.expression = Objects.requireNonNull(expression);
        if (path.isEmpty() || pathElements.stream().anyMatch(String::isEmpty))
            throw new IllegalArgumentException("Invalid field path '" + path + "'");
    }

    public String getPath() { return path; }

    public Expression getInnerExpression() { return expression; }

    @Override
    public boolean isMutating() { return expression.isMutating(); }

    @Override
    public ForEachFieldExpression convertChildren(ExpressionConverter converter) {
        Expression converted = converter.convert(expression);
        return converted != null ? new ForEachFieldExpression(path, converted) : null;
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
        expression.setStatementOutput(documentType, field);
    }

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        super.setInputType(inputType, context);
        if (inputType == null) return null;

        DataType leafType = leafType(inputType, 0);
        DataType leafOutputType = expression.setInputType(leafType, context);
        if (leafOutputType != null && ! leafOutputType.isAssignableTo(leafType))
            throw new VerificationException(this, "Field '" + path + "' has type " + leafType.getName() +
                                                  " but expression produces " + leafOutputType.getName());
        return inputType;
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        if (outputType == null) return null;
        super.setOutputType(outputType, context);
        if (outputType instanceof AnyDataType) return outputType;

        DataType leafType = leafType(outputType, 0);
        DataType leafInputType = expression.setOutputType(leafType, context);
        if (leafInputType != null && ! leafType.isAssignableTo(leafInputType))
            throw new VerificationException(this, "Field '" + path + "' has type " + leafType.getName() +
                                                  " but expression requires " + leafInputType.getName());
        return outputType;
    }

    /** Returns the type of the values selected by the path from the given index, inside a value of the given type. */
    private DataType leafType(DataType type, int index) {
        if (index == pathElements.size()) return type;

        String name = pathElements.get(index);
        if (type instanceof ArrayDataType array)
            return leafType(array.getNestedType(), index);
        if (type instanceof StructDataType struct) {
            Field field = struct.getField(name);
            if (field == null)
                throw new VerificationException(this, "Struct '" + struct.getName() + "' has no field '" + name + "'");
            return leafType(field.getDataType(), index + 1);
        }
        if (type instanceof MapDataType map) {
            if (name.equals("key"))
                return leafType(map.getKeyType(), index + 1);
            if (name.equals("value"))
                return leafType(map.getValueType(), index + 1);
            throw new VerificationException(this, "Expected 'key' or 'value' to select from map " + map.getName() +
                                                  ", got '" + name + "'");
        }
        throw new VerificationException(this, "Expected Array, Struct or Map input when selecting '" + name +
                                              "', got " + type.getName());
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getCurrentValue();
        context.setCurrentValue(apply(input, 0, context));
    }

    /** Returns a copy of the given value, where the values selected by the path from the given index are replaced. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private FieldValue apply(FieldValue value, int index, ExecutionContext context) {
        if (index == pathElements.size()) {
            context.setCurrentValue(value).execute(expression);
            FieldValue result = context.getCurrentValue();
            return result != null ? result : value;
        }

        String name = pathElements.get(index);
        if (value instanceof Array<?> array) {
            Array result = new Array(array.getDataType(), array.size());
            for (FieldValue element : array.getValues())
                result.add(apply(element, index, context));
            return result;
        }
        if (value instanceof Struct struct) {
            FieldValue fieldValue = struct.getFieldValue(name);
            if (fieldValue == null) return struct;
            Struct result = struct.clone();
            result.setFieldValue(name, apply(fieldValue, index + 1, context));
            return result;
        }
        if (value instanceof MapFieldValue<?, ?> map) {
            boolean isKey = name.equals("key");
            MapFieldValue result = new MapFieldValue(map.getDataType(), map.size());
            for (Map.Entry<? extends FieldValue, ? extends FieldValue> entry : map.entrySet()) {
                FieldValue key = isKey ? apply(entry.getKey(), index + 1, context) : entry.getKey();
                FieldValue mapValue = isKey ? entry.getValue() : apply(entry.getValue(), index + 1, context);
                result.put(key, mapValue);
            }
            return result;
        }
        throw new IllegalArgumentException("Expected Array, Struct or Map input when selecting '" + name +
                                           "', got " + value.getDataType().getName());
    }

    @Override
    public String toString() {
        return "for_each_field \"" + path + "\" { " + expression + " }";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ForEachFieldExpression rhs)) return false;
        if (!path.equals(rhs.path)) return false;
        if (!expression.equals(rhs.expression)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + path.hashCode() + expression.hashCode();
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
        select(expression, predicate, operation);
    }

}
