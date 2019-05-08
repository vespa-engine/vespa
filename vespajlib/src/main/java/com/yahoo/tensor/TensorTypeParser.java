// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class for parsing a tensor type spec.
 *
 * @author geirst
 * @author bratseth
 */
public class TensorTypeParser {

    private final static String START_STRING = "tensor";
    private final static String END_STRING = ")";

    private static final Pattern indexedPattern = Pattern.compile("(\\w+)\\[(\\d*)\\]");
    private static final Pattern mappedPattern = Pattern.compile("(\\w+)\\{\\}");

    public static TensorType fromSpec(String specString) {
        if ( ! specString.startsWith(START_STRING) || ! specString.endsWith(END_STRING))
            throw formatException(specString);
        String specBody = specString.substring(START_STRING.length(), specString.length() - END_STRING.length());

        String dimensionsSpec;
        TensorType.Value valueType;
        if (specBody.startsWith("(")) {
            valueType = TensorType.Value.DOUBLE; // no value type spec: Use default
            dimensionsSpec = specBody.substring(1);
        }
        else {
            int parenthesisIndex = specBody.indexOf("(");
            if (parenthesisIndex < 0)
                throw formatException(specString);
            valueType = parseValueTypeSpec(specBody.substring(0, parenthesisIndex), specString);
            dimensionsSpec = specBody.substring(parenthesisIndex + 1);
        }

        if (dimensionsSpec.isEmpty()) return new TensorType.Builder(valueType, Collections.emptyList()).build();

        List<TensorType.Dimension> dimensions = new ArrayList<>();
        for (String element : dimensionsSpec.split(",")) {
            String trimmedElement = element.trim();
            boolean success = tryParseIndexedDimension(trimmedElement, dimensions) ||
                              tryParseMappedDimension(trimmedElement, dimensions);
            if ( ! success)
                throw formatException(specString, "Dimension '" + element + "' is on the wrong format");
        }
        return new TensorType.Builder(valueType, dimensions).build();
    }

    public static TensorType.Value toValueType(String valueTypeString) {
        switch (valueTypeString) {
            case "double" : return TensorType.Value.DOUBLE;
            case "float" : return TensorType.Value.FLOAT;
            default : throw new IllegalArgumentException("Value type must be either 'double' or 'float'" +
                                                         " but was '" + valueTypeString + "'");
        }
    }

    private static TensorType.Value parseValueTypeSpec(String valueTypeSpec, String fullSpecString) {
        if ( ! valueTypeSpec.startsWith("<") || ! valueTypeSpec.endsWith(">"))
            throw formatException(fullSpecString, Optional.of("Value type spec must be enclosed in <>"));

        try {
            return toValueType(valueTypeSpec.substring(1, valueTypeSpec.length() - 1));
        }
        catch (IllegalArgumentException e) {
            throw formatException(fullSpecString, e.getMessage());
        }
    }

    private static boolean tryParseIndexedDimension(String element, List<TensorType.Dimension> dimensions) {
        Matcher matcher = indexedPattern.matcher(element);
        if (matcher.matches()) {
            String dimensionName = matcher.group(1);
            String dimensionSize = matcher.group(2);
            if (dimensionSize.isEmpty()) {
                dimensions.add(TensorType.Dimension.indexed(dimensionName));
            } else {
                dimensions.add(TensorType.Dimension.indexed(dimensionName, Integer.valueOf(dimensionSize)));
            }
            return true;
        }
        return false;
    }

    private static boolean tryParseMappedDimension(String element, List<TensorType.Dimension> dimensions) {
        Matcher matcher = mappedPattern.matcher(element);
        if (matcher.matches()) {
            String dimensionName = matcher.group(1);
            dimensions.add(TensorType.Dimension.mapped(dimensionName));
            return true;
        }
        return false;
    }


    private static IllegalArgumentException formatException(String spec) {
        return formatException(spec, Optional.empty());
    }

    private static IllegalArgumentException formatException(String spec, String errorDetail) {
        return formatException(spec, Optional.of(errorDetail));
    }

    private static IllegalArgumentException formatException(String spec, Optional<String> errorDetail) {
        throw new IllegalArgumentException("A tensor type spec must be on the form " +
                                           "tensor[<valuetype>]?(dimensionidentifier[{}|[length?]*), but was '" + spec + "'. " +
                                           errorDetail.map(s -> s + ". ").orElse("") +
                                           "Examples: tensor(x[]), tensor<float>(name{}, x[10])");
    }

}

