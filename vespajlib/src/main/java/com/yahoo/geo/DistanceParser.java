// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.geo;

import com.yahoo.api.annotations.Beta;

/**
 * Utility for parsing a geographical distance with unit.
 */
@Beta
public class DistanceParser {

    // according to wikipedia:
    // Earth's equatorial radius = 6378137 meter - not used
    // meters per mile = 1609.344
    // 180 degrees equals one half diameter equals PI*r
    // Earth's polar radius = 6356752 meter

    public final static double  m2deg =            180.0 / (Math.PI * 6356752.0);
    public final static double km2deg = 1000.000 * 180.0 / (Math.PI * 6356752.0);
    public final static double mi2deg = 1609.344 * 180.0 / (Math.PI * 6356752.0);

    private final double degrees;

    public double getDegrees() { return degrees; }

    /**
     * Parse a distance in some kind of units, converting to geographical degrees.
     * Note that the number and the unit should be separated by a single space only,
     * or not separated at all.
     * Supported units are "m", "km", "miles", and "deg",
     * the last one meaning degrees with no conversion.
     * For brevity "mi" = "miles" and "d" = "deg".
     **/
    static public double parse(String distance) {
        var parser = new DistanceParser(distance, false);
        return parser.degrees;
    }

    DistanceParser(String distance, boolean assumeMicroDegrees) {
        if (distance.endsWith(" km")) {
            double km = Double.valueOf(distance.substring(0, distance.length()-3));
            degrees = km * km2deg;
        } else if (distance.endsWith(" m")) {
            double meters = Double.valueOf(distance.substring(0, distance.length()-2));
            degrees = meters * m2deg;
        } else if (distance.endsWith(" miles")) {
            double miles = Double.valueOf(distance.substring(0, distance.length()-6));
            degrees = miles * mi2deg;
        } else if (distance.endsWith(" mi")) {
            double miles = Double.valueOf(distance.substring(0, distance.length()-3));
            degrees = miles * mi2deg;
        } else if (distance.endsWith(" deg")) {
            degrees = Double.valueOf(distance.substring(0, distance.length()-4));
        } else if (distance.endsWith(" d")) {
            degrees = Double.valueOf(distance.substring(0, distance.length()-2));
        } else if (distance.endsWith("km")) {
            double km = Double.valueOf(distance.substring(0, distance.length()-2));
            degrees = km * km2deg;
        } else if (distance.endsWith("m")) {
            double meters = Double.valueOf(distance.substring(0, distance.length()-1));
            degrees = meters * m2deg;
        } else if (distance.endsWith("miles")) {
            double miles = Double.valueOf(distance.substring(0, distance.length()-5));
            degrees = miles * mi2deg;
        } else if (distance.endsWith("mi")) {
            double miles = Double.valueOf(distance.substring(0, distance.length()-2));
            degrees = miles * mi2deg;
        } else if (distance.endsWith("deg")) {
            degrees = Double.valueOf(distance.substring(0, distance.length()-3));
        } else if (distance.endsWith("d")) {
            degrees = Double.valueOf(distance.substring(0, distance.length()-1));
        } else if (assumeMicroDegrees) {
            degrees = Integer.parseInt(distance) * 0.000001;
        } else {
            throw new IllegalArgumentException("missing unit for distance: "+distance);
        }
    }

}
