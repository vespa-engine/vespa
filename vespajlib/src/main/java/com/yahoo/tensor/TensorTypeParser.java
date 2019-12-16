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
        return fromSpec(specString, null);
    }

    /**
     * @param dimensionOrder if not null, this will be populated with the dimension names in the order they are written
     */
    static TensorType fromSpec(String specString, List<String> dimensionOrder) {
        specString = specString.trim();
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
            TensorType.Dimension dimension = tryParseIndexedDimension(trimmedElement);
            if (dimension == null)
                dimension = tryParseMappedDimension(trimmedElement);
            if (dimension == null)
                throw formatException(specString, "Dimension '" + element + "' is on the wrong format");
            dimensions.add(dimension);
            if (dimensionOrder != null)
                dimensionOrder.add(dimension.name());
        }
        return new TensorType.Builder(valueType, dimensions).build();
    }

    private static TensorType.Value parseValueTypeSpec(String valueTypeSpec, String fullSpecString) {
        if ( ! valueTypeSpec.startsWith("<") || ! valueTypeSpec.endsWith(">"))
            throw formatException(fullSpecString, Optional.of("Value type spec must be enclosed in <>"));

        try {
            return TensorType.Value.fromId(valueTypeSpec.substring(1, valueTypeSpec.length() - 1));
        }
        catch (IllegalArgumentException e) {
            throw formatException(fullSpecString, e.getMessage());
        }
    }

    private static TensorType.Dimension tryParseIndexedDimension(String element) {
        Matcher matcher = indexedPattern.matcher(element);
        if (matcher.matches()) {
            String dimensionName = matcher.group(1);
            String dimensionSize = matcher.group(2);
            if (dimensionSize.isEmpty())
                return TensorType.Dimension.indexed(dimensionName);
            else
                return TensorType.Dimension.indexed(dimensionName, Integer.valueOf(dimensionSize));
        }
        return null;
    }

    private static TensorType.Dimension tryParseMappedDimension(String element) {
        Matcher matcher = mappedPattern.matcher(element);
        if (matcher.matches()) {
            String dimensionName = matcher.group(1);
            return TensorType.Dimension.mapped(dimensionName);
        }
        return null;
    }


    private static IllegalArgumentException formatException(String spec) {
        return formatException(spec, Optional.empty());
    }

    private static IllegalArgumentException formatException(String spec, String errorDetail) {
        return formatException(spec, Optional.of(errorDetail));
    }

    private static IllegalArgumentException formatException(String spec, Optional<String> errorDetail) {
        throw new IllegalArgumentException("A tensor type spec must be on the form " +
                                           "tensor[<valuetype>]?(dimensionidentifier[{}|[length]*), but was '" + spec + "'. " +
                                           errorDetail.map(s -> s + ". ").orElse("") +
                                           "Examples: tensor(x[3]), tensor<float>(name{}, x[10])");
    }

}

