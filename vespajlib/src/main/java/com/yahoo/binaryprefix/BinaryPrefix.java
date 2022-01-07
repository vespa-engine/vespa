// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.binaryprefix;

/**
 * Represents binary prefixes.
 *
 * @author Tony Vaagenes
 */
public enum BinaryPrefix {

    //represents the binary prefix 2^(k*10)
    unit(0),
    kilo(1, 'K'),
    mega(2, 'M'),
    giga(3, 'G'),
    tera(4, 'T'),
    peta(5, 'P'),
    exa(6, 'E'),
    zetta(7, 'Z'),
    yotta(8, 'Y');

    private final int k;
    public final char symbol;

    private BinaryPrefix(int k, char symbol) {
        this.k = k;
        this.symbol = symbol;
    }

    private BinaryPrefix(int k) {
        this(k, (char)0);
    }

    /* In most cases, BinaryScaledAmount should be prefered instead of this */
    public double convertFrom(double value, BinaryPrefix binaryPrefix) {
        return value * Math.pow(2,
                10 * (binaryPrefix.k - k));
    }

    public static BinaryPrefix fromSymbol(char c) {
        for (BinaryPrefix binaryPrefix : values()) {
            if (binaryPrefix.symbol == c)
                return binaryPrefix;
        }
        throw new RuntimeException("No such binary prefix: " + c);
    }

}
