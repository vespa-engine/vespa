// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.detect;

/**
 * A hint that can be given to a {@link Detector}.
 *
 * @author Einar M R Rosenvinge
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
