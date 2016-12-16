// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.annotations.Beta;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class for parsing a tensor type spec.
 *
 * @author geirst
 */
@Beta
class TensorTypeParser {

    private final static String START_STRING = "tensor(";
    private final static String END_STRING = ")";

    private static final Pattern indexedPattern = Pattern.compile("(\\w+)\\[(\\d*)\\]");
    private static final Pattern mappedPattern = Pattern.compile("(\\w+)\\{\\}");

    static TensorType fromSpec(String specString) {
        if (!specString.startsWith(START_STRING) || !specString.endsWith(END_STRING)) {
            throw new IllegalArgumentException("Tensor type spec must start with '" + START_STRING + "'" +
                    " and end with '" + END_STRING + "', but was '" + specString + "'");
        }
        TensorType.Builder builder = new TensorType.Builder();
        String dimensionsSpec = specString.substring(START_STRING.length(), specString.length() - END_STRING.length());
        if (dimensionsSpec.isEmpty()) {
            return builder.build();
        }
        for (String element : dimensionsSpec.split(",")) {
            String trimmedElement = element.trim();
            if (tryParseIndexedDimension(trimmedElement, builder)) {
            } else if (tryParseMappedDimension(trimmedElement, builder)) {
            } else {
                throw new IllegalArgumentException("Failed parsing element '" + element +
                        "' in type spec '" + specString + "'");
            }
        }
        return builder.build();
    }

    private static boolean tryParseIndexedDimension(String element, TensorType.Builder builder) {
        Matcher matcher = indexedPattern.matcher(element);
        if (matcher.matches()) {
            String dimensionName = matcher.group(1);
            String dimensionSize = matcher.group(2);
            if (dimensionSize.isEmpty()) {
                builder.indexed(dimensionName);
            } else {
                builder.indexed(dimensionName, Integer.valueOf(dimensionSize));
            }
            return true;
        }
        return false;
    }

    private static boolean tryParseMappedDimension(String element, TensorType.Builder builder) {
        Matcher matcher = mappedPattern.matcher(element);
        if (matcher.matches()) {
            String dimensionName = matcher.group(1);
            builder.mapped(dimensionName);
            return true;
        }
        return false;
    }

}

