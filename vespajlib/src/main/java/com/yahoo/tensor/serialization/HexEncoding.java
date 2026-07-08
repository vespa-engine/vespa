// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.yahoo.tensor.TensorType;

import java.util.Optional;

/**
 * Utility for encoding and decoding of tensors as hex strings.
 *
 * @author bratseth
 */
public class HexEncoding {

    /** Returns the reason this isn't a valid hex string, or empty if it is valid. */
    public static Optional<String> validateHex(String hex, TensorType targetType) {
        long expectedValueCount = denseValueCount(targetType);
        if (expectedValueCount == 0 || targetType.dimensions().isEmpty()) return Optional.of("Tensor type has zero values");

        // Use a generic message as the user may not have meant to end up here when supplying non-hex characters
        if (hex.chars().anyMatch(ch -> (Character.digit(ch, 16) == -1)))
            return Optional.of("Expected a number, hex string, or a string starting by {, [ or tensor(...)");

        long expectedHexDigits = expectedHexDigitCount(expectedValueCount, targetType.valueType());
        if (hex.length() != expectedHexDigits)
            return Optional.of("Expected " + expectedHexDigits + " hex digits, but got " + hex.length());
        return Optional.empty();
    }

    /**
     * Returns the values encoded in the given hex string given it is an encoding
     * of the given target type.
     * This supports implicit decoding into a different numeric precision than the hex encoded values:
     * Useful when converting to a different value type,
     */
    public static double[] decodeHex(String hex, TensorType targetType) {
        int expectedValueCount = denseValueCount(targetType);
        if (expectedValueCount == 0) {
            if (hex.isEmpty()) return new double[0];
            throw new IllegalArgumentException("Hex value must be empty for " + targetType);
        }

        var hexValueType = inferHexValueType(hex.length(), expectedValueCount, targetType);
        return switch (hexValueType) {
            case INT8 -> decodeHexStringAsBytes(hex);
            case BFLOAT16 -> decodeHexStringAsBFloat16s(hex);
            case FLOAT -> decodeHexStringAsFloats(hex);
            case DOUBLE -> decodeHexStringAsDoubles(hex);
        };
    }

    private static int denseValueCount(TensorType type) {
        long valueCount = 1;
        for (var dimension : type.dimensions()) {
            if (dimension.isMapped()) continue;
            if (dimension.size().isEmpty()) {
                throw new IllegalArgumentException("Hex values can only be used with bound dimensions in " + type);
            }
            valueCount *= dimension.size().get();
        }
        return Math.toIntExact(valueCount);
    }

    private static TensorType.Value inferHexValueType(long hexLength, long expectedValueCount, TensorType targetType) {
        long expectedHexDigits = expectedHexDigitCount(expectedValueCount, targetType.valueType());
        if (hexLength % expectedValueCount != 0)
            throw new IllegalArgumentException("Unexpected hex length: Expected " + expectedHexDigits +
                                               " hex digits for " + targetType + " but got " + hexLength);

        var hexValueType = inferHexValueType(hexLength, expectedValueCount);
        if (! isCompatible(hexValueType, targetType.valueType()))
            throw new IllegalArgumentException("Unexpected hex length: Expected " + expectedHexDigits +
                                               " hex digits for " + targetType + " but got " + hexLength);

        return hexValueType;
    }

    private static TensorType.Value inferHexValueType(long hexLength, long expectedValueCount) {
        int hexDigitsPerCell = (int)(hexLength / expectedValueCount);
        return switch (hexDigitsPerCell) {
            case 2 -> TensorType.Value.INT8;
            case 4 -> TensorType.Value.BFLOAT16;
            case 8 -> TensorType.Value.FLOAT;
            case 16 -> TensorType.Value.DOUBLE;
            default -> throw new IllegalArgumentException("Unexpected hex digits per cell: Expecting 2, 4, 8 or 16, but was " +
                                                          hexDigitsPerCell);
        };
    }

    private static boolean isCompatible(TensorType.Value hexType, TensorType.Value targetType) {
        if (hexType == targetType) return true;
        if (targetType == TensorType.Value.INT8) return false; // Don't allow implicit conversion to int8
        return true; // Allow implicit conversion to/from bfloat16, float, double, and from int8
    }

    private static long expectedHexDigitCount(long expectedValueCount, TensorType.Value valueType) {
        int hexDigitsPerCell = switch (valueType) {
            case INT8 -> 2;
            case BFLOAT16 -> 4;
            case FLOAT -> 8;
            case DOUBLE -> 16;
        };
        return expectedValueCount * hexDigitsPerCell;
    }

    private static double[] decodeHexStringAsBytes(String input) {
        int l = input.length() / 2;
        double[] result = new double[l];
        int idx = 0;
        for (int i = 0; i < l; i++) {
            byte v = decodeHex(input, idx++);
            v <<= 4;
            v += decodeHex(input, idx++);
            result[i] = v;
        }
        return result;
    }

    private static double[] decodeHexStringAsBFloat16s(String input) {
        int l = input.length() / 4;
        double[] result = new double[l];
        int idx = 0;
        for (int i = 0; i < l; i++) {
            int v = decodeHex(input, idx++);
            v <<= 4; v += decodeHex(input, idx++);
            v <<= 4; v += decodeHex(input, idx++);
            v <<= 4; v += decodeHex(input, idx++);
            v <<= 16;
            result[i] = Float.intBitsToFloat(v);
        }
        return result;
    }

    private static double[] decodeHexStringAsFloats(String input) {
        int l = input.length() / 8;
        double[] result = new double[l];
        int idx = 0;
        for (int i = 0; i < l; i++) {
            int v = 0;
            for (int j = 0; j < 8; j++) {
                v <<= 4;
                v += decodeHex(input, idx++);
            }
            result[i] = Float.intBitsToFloat(v);
        }
        return result;
    }

    private static double[] decodeHexStringAsDoubles(String input) {
        int l = input.length() / 16;
        double[] result = new double[l];
        int idx = 0;
        for (int i = 0; i < l; i++) {
            long v = 0;
            for (int j = 0; j < 16; j++) {
                v <<= 4;
                v += decodeHex(input, idx++);
            }
            result[i] = Double.longBitsToDouble(v);
        }
        return result;
    }

    private static byte decodeHex(String input, int index) {
        int d = Character.digit(input.charAt(index), 16);
        if (d < 0) {
            throw new IllegalArgumentException("Invalid digit '" + input.charAt(index) +
                                               "' at index " + index + " in input " + input);
        }
        return (byte)d;
    }

}
