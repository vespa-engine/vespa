// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.fasterxml.jackson.core.JsonToken;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentId;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.json.TokenBuffer;
import com.yahoo.document.update.ValueUpdate;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SingleValueReader {

    public static final String UPDATE_ASSIGN = "assign";
    public static final String UPDATE_INCREMENT = "increment";
    public static final String UPDATE_DECREMENT = "decrement";
    public static final String UPDATE_MULTIPLY = "multiply";
    public static final String UPDATE_DIVIDE = "divide";

    public static final Map<String, String> UPDATE_OPERATION_TO_ARITHMETIC_SIGN = new HashMap<>();
    public static final Map<String, String> ARITHMETIC_SIGN_TO_UPDATE_OPERATION;
    private static final Pattern arithmeticExpressionPattern;

    static {
        UPDATE_OPERATION_TO_ARITHMETIC_SIGN.put(UPDATE_INCREMENT, "+");
        UPDATE_OPERATION_TO_ARITHMETIC_SIGN.put(UPDATE_DECREMENT, "-");
        UPDATE_OPERATION_TO_ARITHMETIC_SIGN.put(UPDATE_MULTIPLY, "*");
        UPDATE_OPERATION_TO_ARITHMETIC_SIGN.put(UPDATE_DIVIDE, "/");
        ARITHMETIC_SIGN_TO_UPDATE_OPERATION = UPDATE_OPERATION_TO_ARITHMETIC_SIGN.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        String validSigns = Pattern.quote(String.join("", UPDATE_OPERATION_TO_ARITHMETIC_SIGN.values()));
        arithmeticExpressionPattern = Pattern.compile("^\\$\\w+\\s*([" + validSigns + "])\\s*(\\d+(.\\d+)?)$");
    }

    public static FieldValue readSingleValue(TokenBuffer buffer, DataType expectedType, boolean ignoreUndefinedFields) {
        if (buffer.currentToken().isScalarValue()) {
            return readAtomic(buffer.currentText(), expectedType);
        } else {
            FieldValue fieldValue = expectedType.createFieldValue();
            CompositeReader.populateComposite(buffer, fieldValue, ignoreUndefinedFields);
            return fieldValue;
        }
    }

    @SuppressWarnings("rawtypes")
    public static ValueUpdate readSingleUpdate(TokenBuffer buffer, DataType expectedType, String action, boolean ignoreUndefinedFields) {
        return switch (action) {
            case UPDATE_ASSIGN -> (buffer.currentToken() == JsonToken.VALUE_NULL)
                                  ? ValueUpdate.createClear()
                                  : ValueUpdate.createAssign(readSingleValue(buffer, expectedType, ignoreUndefinedFields));
            // double is silly, but it's what is used internally anyway
            case UPDATE_INCREMENT -> ValueUpdate.createIncrement(Double.valueOf(buffer.currentText()));
            case UPDATE_DECREMENT -> ValueUpdate.createDecrement(Double.valueOf(buffer.currentText()));
            case UPDATE_MULTIPLY -> ValueUpdate.createMultiply(Double.valueOf(buffer.currentText()));
            case UPDATE_DIVIDE -> ValueUpdate.createDivide(Double.valueOf(buffer.currentText()));
            default -> throw new IllegalArgumentException("Operation '" + buffer.currentName() + "' not implemented.");
        };
    }

    public static Matcher matchArithmeticOperation(String expression) {
        return arithmeticExpressionPattern.matcher(expression.trim());
    }

    public static FieldValue readAtomic(String field, DataType expectedType) {
        if (expectedType.equals(PositionDataType.INSTANCE)) {
            return PositionDataType.fromString(field);
        } else if (expectedType instanceof ReferenceDataType) {
            return readReferenceFieldValue(field, expectedType);
        } else {
            return expectedType.createFieldValue(field);
        }
    }

    private static FieldValue readReferenceFieldValue(String refText, DataType expectedType) {
        final FieldValue value = expectedType.createFieldValue();
        if (!refText.isEmpty()) {
            value.assign(new DocumentId(refText));
        }
        return value;
    }
}
