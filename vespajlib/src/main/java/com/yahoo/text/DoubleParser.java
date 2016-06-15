// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

/**
 * Utility class to parse a String into a double.
 * <p>
 * This is intended as a lower-cost replacement for the standard
 * Double.parseDouble(String) since that method will cause lock
 * contention if it's used too often.
 * <p>
 * Note that this implementation won't always produce the same results
 * as java.lang.Double (low-order bits may differ), and it doesn't
 * properly support denormalized numbers.
 * <p>
 * Also, this implementation is very poorly tested at the moment, so
 * it should be used carefully, only in cases where you know the input
 * will be well-defined and you don't need full precision.
 *
 * @author arnej27959
 */
public final class DoubleParser {

    /**
     * Utility method that parses a String and returns a double.
     *
     * @param  data the String to parse
     * @return double parsed value of the string
     * @throws NumberFormatException if the string is not a well-formatted number
     * @throws NullPointerException if the string is a null pointer
     */
    public static double parse(String data) {
        final int len = data.length();
        double result = 0;
        boolean negative = false;
        int beforePoint = 0;
        int exponent = 0;
        byte[] digits = new byte[25];
        int numDigits = 0;

        int i = 0;
        while (i < len && Character.isWhitespace(data.charAt(i))) {
            i++;
        }
        if (data.charAt(i) == '+') {
            i++;
        } else if (data.charAt(i) == '-') {
            negative = true;
            i++;
        }
        if (i + 3 <= len && data.substring(i, i+3).equals("NaN")) {
            i += 3;
            result = Double.NaN;
        } else if (i + 8 <= len && data.substring(i, i+8).equals("Infinity")) {
            i += 8;
            if (negative) {
                result = Double.NEGATIVE_INFINITY;
            } else {
                result = Double.POSITIVE_INFINITY;
            }
        } else {
            while (i < len && Character.isDigit(data.charAt(i))) {
                int dval = Character.digit(data.charAt(i), 10);
                assert dval >= 0;
                assert dval < 10;
                if (numDigits < 25) {
                    digits[numDigits++] = (byte)dval;
                }
                ++beforePoint;
                i++;
            }
            if (i < len && data.charAt(i) == '.') {
                i++;
                while (i < len && Character.isDigit(data.charAt(i))) {
                    int dval = Character.digit(data.charAt(i), 10);
                    assert dval >= 0;
                    assert dval < 10;
                    if (numDigits < 25) {
                        digits[numDigits++] = (byte)dval;
                    }
                    i++;
                }
            }
            if (numDigits == 0) {
                throw new NumberFormatException("No digits in number: '"+data+"'");
            }
            if (i < len && (data.charAt(i) == 'e' || data.charAt(i) == 'E')) {
                i++;
                boolean expNeg = false;
                int expDigits = 0;
                if (data.charAt(i) == '+') {
                    i++;
                } else if (data.charAt(i) == '-') {
                    expNeg = true;
                    i++;
                }
                while (i < len && Character.isDigit(data.charAt(i))) {
                    int dval = Character.digit(data.charAt(i), 10);
                    assert dval >= 0;
                    assert dval < 10;
                    exponent *= 10;
                    exponent += dval;
                    ++expDigits;
                    i++;
                }
                if (expDigits == 0) {
                    throw new NumberFormatException("Missing digits in exponent part: "+data);
                }
                if (expNeg) {
                    exponent = -exponent;
                }
            }
            // System.out.println("parsed exp: "+exponent);
            // System.out.println("before pt: "+beforePoint);
            exponent += beforePoint;
            exponent -= numDigits;
            // System.out.println("adjusted exp: "+exponent);
            for (int d = numDigits; d > 0; d--) {
                double dv = digits[d-1];
                dv *= powTen(numDigits - d);
                result += dv;
            }
            // System.out.println("digits sum: "+result);
            while (exponent < -99) {
                result *= powTen(-99);
                exponent += 99;
            }
            while (exponent > 99) {
                result *= powTen(99);
                exponent -= 99;
            }
            // System.out.println("digits sum: "+result);
            // System.out.println("exponent multiplier: "+powTen(exponent));
            result *= powTen(exponent);

            if (negative) {
                result = -result;
            }
        }
        while (i < len && Character.isWhitespace(data.charAt(i))) {
            i++;
        }
        if (i < len) {
            throw new NumberFormatException("Extra characters after number: "+data.substring(i));
        }
        return result;
    }

    private static double[] tens = {
        1.0e00, 1.0e01, 1.0e02, 1.0e03, 1.0e04, 1.0e05, 1.0e06,
        1.0e07, 1.0e08, 1.0e09, 1.0e10, 1.0e11, 1.0e12, 1.0e13,
        1.0e14, 1.0e15, 1.0e16, 1.0e17, 1.0e18, 1.0e19, 1.0e20,
        1.0e21, 1.0e22, 1.0e23, 1.0e24, 1.0e25, 1.0e26, 1.0e27,
        1.0e28, 1.0e29, 1.0e30, 1.0e31, 1.0e32, 1.0e33, 1.0e34,
        1.0e35, 1.0e36, 1.0e37, 1.0e38, 1.0e39, 1.0e40, 1.0e41,
        1.0e42, 1.0e43, 1.0e44, 1.0e45, 1.0e46, 1.0e47, 1.0e48,
        1.0e49, 1.0e50, 1.0e51, 1.0e52, 1.0e53, 1.0e54, 1.0e55,
        1.0e56, 1.0e57, 1.0e58, 1.0e59, 1.0e60, 1.0e61, 1.0e62,
        1.0e63, 1.0e64, 1.0e65, 1.0e66, 1.0e67, 1.0e68, 1.0e69,
        1.0e70, 1.0e71, 1.0e72, 1.0e73, 1.0e74, 1.0e75, 1.0e76,
        1.0e77, 1.0e78, 1.0e79, 1.0e80, 1.0e81, 1.0e82, 1.0e83,
        1.0e84, 1.0e85, 1.0e86, 1.0e87, 1.0e88, 1.0e89, 1.0e90,
        1.0e91, 1.0e92, 1.0e93, 1.0e94, 1.0e95, 1.0e96, 1.0e97,
        1.0e98, 1.0e99
    };

    private static double[] tenths = {
        1.0e-00, 1.0e-01, 1.0e-02, 1.0e-03, 1.0e-04, 1.0e-05,
        1.0e-06, 1.0e-07, 1.0e-08, 1.0e-09, 1.0e-10, 1.0e-11,
        1.0e-12, 1.0e-13, 1.0e-14, 1.0e-15, 1.0e-16, 1.0e-17,
        1.0e-18, 1.0e-19, 1.0e-20, 1.0e-21, 1.0e-22, 1.0e-23,
        1.0e-24, 1.0e-25, 1.0e-26, 1.0e-27, 1.0e-28, 1.0e-29,
        1.0e-30, 1.0e-31, 1.0e-32, 1.0e-33, 1.0e-34, 1.0e-35,
        1.0e-36, 1.0e-37, 1.0e-38, 1.0e-39, 1.0e-40, 1.0e-41,
        1.0e-42, 1.0e-43, 1.0e-44, 1.0e-45, 1.0e-46, 1.0e-47,
        1.0e-48, 1.0e-49, 1.0e-50, 1.0e-51, 1.0e-52, 1.0e-53,
        1.0e-54, 1.0e-55, 1.0e-56, 1.0e-57, 1.0e-58, 1.0e-59,
        1.0e-60, 1.0e-61, 1.0e-62, 1.0e-63, 1.0e-64, 1.0e-65,
        1.0e-66, 1.0e-67, 1.0e-68, 1.0e-69, 1.0e-70, 1.0e-71,
        1.0e-72, 1.0e-73, 1.0e-74, 1.0e-75, 1.0e-76, 1.0e-77,
        1.0e-78, 1.0e-79, 1.0e-80, 1.0e-81, 1.0e-82, 1.0e-83,
        1.0e-84, 1.0e-85, 1.0e-86, 1.0e-87, 1.0e-88, 1.0e-89,
        1.0e-90, 1.0e-91, 1.0e-92, 1.0e-93, 1.0e-94, 1.0e-95,
        1.0e-96, 1.0e-97, 1.0e-98, 1.0e-99
    };

    private static double powTen(int exponent) {
        if (exponent > 0) {
            assert exponent < 100;
            return tens[exponent];
        }
        if (exponent < 0) {
            exponent = -exponent;
            assert exponent < 100;
            return tenths[exponent];
        }
        return 1.0;
    }

}
