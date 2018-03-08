// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.annotations.Beta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class for parsing a tensor type spec.
 *
 * @author geirst
 */
@Beta
public class TensorTypeParser {

    private final static String START_STRING = "tensor(";
    private final static String END_STRING = ")";

    private static final Pattern indexedPattern = Pattern.compile("(\\w+)\\[(\\d*)\\]");
    private static final Pattern mappedPattern = Pattern.compile("(\\w+)\\{\\}");

    public static TensorType fromSpec(String specString) {
        return new TensorType.Builder(dimensionsFromSpec(specString)).build();
    }

    public static List<TensorType.Dimension> dimensionsFromSpec(String specString) {
        if ( ! specString.startsWith(START_STRING) || !specString.endsWith(END_STRING)) {
            throw new IllegalArgumentException("Tensor type spec must start with '" + START_STRING + "'" +
                                               " and end with '" + END_STRING + "', but was '" + specString + "'");
        }
        String dimensionsSpec = specString.substring(START_STRING.length(), specString.length() - END_STRING.length());
        if (dimensionsSpec.isEmpty()) return Collections.emptyList();

        List<TensorType.Dimension> dimensions = new ArrayList<>();
        for (String element : dimensionsSpec.split(",")) {
            String trimmedElement = element.trim();
            boolean success = tryParseIndexedDimension(trimmedElement, dimensions) ||
                              tryParseMappedDimension(trimmedElement, dimensions);
            if ( ! success)
                throw new IllegalArgumentException("Failed parsing element '" + element +
                                                   "' in type spec '" + specString + "'");
        }
        return dimensions;
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

}

