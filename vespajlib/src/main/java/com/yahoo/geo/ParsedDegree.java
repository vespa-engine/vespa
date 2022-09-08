// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.geo;

/**
 * Utility for holding one geographical coordinate
 *
 * @author arnej27959
 */
public class ParsedDegree {

    /**
     * The parsed latitude or longitude
     * Degrees north or east if positive
     * Degrees south or west if negative
     */
    public final double degrees;

    // one of these two flag will be true:
    public final boolean isLatitude;
    public final boolean isLongitude;

    public ParsedDegree(double value, boolean isLat, boolean isLon) {
        this.degrees = value;
        this.isLatitude = isLat;
        this.isLongitude = isLon;
        if (isLat && isLon) {
            throw new IllegalArgumentException("value cannot be both latitude and longitude at once");
        }
        if (isLat || isLon) {
            return;
        }
        throw new IllegalArgumentException("value must be either latitude or longitude");
    }

    static public ParsedDegree fromString(String toParse, boolean assumeLatitude, boolean assumeLongitude) {
        if (assumeLatitude && assumeLongitude) {
            throw new IllegalArgumentException("value cannot be both latitude and longitude at once");
        }
        var parser = new OneDegreeParser(assumeLatitude, toParse);
        if (parser.foundLatitude) {
            return new ParsedDegree(parser.latitude, true, false);
        }
        if (parser.foundLongitude) {
            return new ParsedDegree(parser.longitude, false, true);
        }
        throw new IllegalArgumentException("could not parse: "+toParse);
    }

    @Override
    public String toString() {
        if (isLatitude) {
            return "Latitude: "+degrees+" degrees";
        } else {
            return "Longitude: "+degrees+" degrees";
        }
    }

}
