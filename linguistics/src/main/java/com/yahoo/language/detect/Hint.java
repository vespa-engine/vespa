// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.detect;

/**
 * <p>A hint that can be given to a {@link Detector}.</p>
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class Hint {

    private final String market;
    private final String country;

    private Hint(String market, String country) {
        this.market = market;
        this.country = country;
    }

    public String getMarket() {
        return market;
    }

    public String getCountry() {
        return country;
    }

    public static Hint newMarketHint(String market) {
        return new Hint(market, null);
    }

    public static Hint newCountryHint(String country) {
        return new Hint(null, country);
    }

    public static Hint newInstance(String market, String country) {
        return new Hint(market, country);
    }
}
