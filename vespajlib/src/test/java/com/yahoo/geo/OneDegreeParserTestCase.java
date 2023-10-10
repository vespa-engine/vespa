// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.geo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the OneDegreeParser class.
 *
 * @author arnej27959
 */
public class OneDegreeParserTestCase {

    private static final double delta = 0.000000000001;

    private OneDegreeParser parser;

    private void checkLat(boolean assumeLatitude, String toParse, double expected) {
        parser = new OneDegreeParser(assumeLatitude, toParse);
        assertEquals(expected, parser.latitude, delta);
        assertTrue(parser.foundLatitude);
        assertFalse(parser.foundLongitude);
    }
    private void checkLon(boolean assumeLatitude, String toParse, double expected) {
        parser = new OneDegreeParser(assumeLatitude, toParse);
        assertEquals(expected, parser.longitude, delta);
        assertFalse(parser.foundLatitude);
        assertTrue(parser.foundLongitude);
    }
    private void checkLat(String toParse, double expected) {
        checkLat(true, toParse, expected);
        checkLat(false, toParse, expected);
    }
    private void checkLon(String toParse, double expected) {
        checkLon(true, toParse, expected);
        checkLon(false, toParse, expected);
    }

    private void checkZeroLat(boolean assumeLatitude, String toParse) {
        checkLat(assumeLatitude, toParse, 0d);
    }

    private void checkZeroLon(boolean assumeLatitude, String toParse) {
        checkLon(assumeLatitude, toParse, 0d);
    }

    /**
     * Tests different inputs that should all produce 0 or -0.
     */
    @Test
    public void testZero() {
        checkZeroLat(true, "0");
        checkZeroLat(true, "0.0");
        checkZeroLat(true, "0o0.0");
        checkZeroLat(true, "0o0'0");
        checkZeroLat(true, "0\u00B00'0");

        checkZeroLon(false, "0");
        checkZeroLon(false, "0.0");
        checkZeroLon(false, "0o0.0");
        checkZeroLon(false, "0o0'0");
        checkZeroLon(false, "0\u00B00'0");

        checkZeroLat(false, "N0");
        checkZeroLat(false, "N0.0");
        checkZeroLat(false, "N0\u00B00'0");
        checkZeroLat(false, "S0");
        checkZeroLat(false, "S0.0");
        checkZeroLat(false, "S0o0'0");
        checkZeroLat(false, "S0\u00B00'0");

        checkZeroLon(true, "E0");
        checkZeroLon(true, "E0.0");
        checkZeroLon(true, "E0\u00B00'0");
        checkZeroLon(true, "W0");
        checkZeroLon(true, "W0.0");
        checkZeroLon(true, "W0o0'0");
        checkZeroLon(true, "W0\u00B00'0");
    }

    /**
     * Tests inputs that are close to 0.
     */
    @Test
    public void testNearZero() {
        checkLat("N0.0001", 0.0001);
        checkLat("S0.0001", -0.0001);
        checkLon("E0.0001", 0.0001);
        checkLon("W0.0001", -0.0001);

        checkLat("N0.000001", 0.000001);
        checkLat("S0.000001", -0.000001);
        checkLon("E0.000001", 0.000001);
        checkLon("W0.000001", -0.000001);

        checkLat("N0\u00B00'1", 1/3600d);
        checkLat("S0\u00B00'1", -1/3600d);
        checkLon("E0\u00B00'1", 1/3600d);
        checkLon("W0\u00B00'1", -1/3600d);
    }

    /**
     * Tests inputs that are close to latitude 90/-90 degrees and longitude 180/-180 degrees.
     */
    @Test
    public void testNearBoundary() {
        checkLat("N89.9999", 89.9999);
        checkLat("S89.9999", -89.9999);
        checkLon("E179.9999", 179.9999);
        checkLon("W179.9999", -179.9999);

        checkLat("N89.999999", 89.999999);
        checkLat("S89.999999", -89.999999);
        checkLon("E179.999999", 179.999999);
        checkLon("W179.999999", -179.999999);

        checkLat("N89\u00B059'59", 89+59/60d+59/3600d);
        checkLat("S89\u00B059'59", -(89+59/60d+59/3600d));
        checkLon("E179\u00B059'59", 179+59/60d+59/3600d);
        checkLon("W179\u00B059'59", -(179+59/60d+59/3600d));
    }

    /**
     * Tests inputs that are on latitude 90/-90 degrees and longitude 180/-180 degrees.
     */
    @Test
    public void testOnBoundary() {
        checkLat("N90", 90d);
        checkLat("N90\u00B00'0", 90d);
        checkLat("S90", -90d);
        checkLat("S90\u00B00'0", -90d);

        checkLon("E180", 180d);
        checkLon("E180\u00B00'0", 180d);
        checkLon("W180", -180d);
        checkLon("W180\u00B00'0", -180d);
    }

    private String parseException(boolean assumeLatitude, String toParse) {
        String message = "";
        try {
            parser = new OneDegreeParser(assumeLatitude, toParse);
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        return message;
    }

    /**
     * Tests inputs that are above latitude 90/-90 degrees and longitude 180/-180 degrees.
     */
    @Test
    public void testAboveBoundary() {
        String message = parseException(false, "N90.0001");
        assertEquals("out of range [-90,+90]: 90.0001 when parsing <N90.0001>", message);
        message = parseException(false, "S90.0001");
        assertEquals("out of range [-90,+90]: -90.0001 when parsing <S90.0001>", message);
        message = parseException(true, "E180.0001");
        assertEquals("out of range [-180,+180]: 180.0001 when parsing <E180.0001>", message);
        message = parseException(true, "W180.0001");
        assertEquals("out of range [-180,+180]: -180.0001 when parsing <W180.0001>", message);
        message = parseException(false, "N90.000001");
        assertEquals("out of range [-90,+90]: 90.000001 when parsing <N90.000001>", message);
        message = parseException(false, "S90.000001");
        assertEquals("out of range [-90,+90]: -90.000001 when parsing <S90.000001>", message);
        message = parseException(true, "E180.000001");
        assertEquals("out of range [-180,+180]: 180.000001 when parsing <E180.000001>", message);
        message = parseException(true, "W180.000001");
        assertEquals("out of range [-180,+180]: -180.000001 when parsing <W180.000001>", message);
    }

    /**
     * Tests various inputs that contain syntax errors.
     */
    @Test
    public void testInputErrors() {
        String message = parseException(false, "N90S90");
        assertEquals("already set direction once, cannot add direction: S when parsing <N90S90>", message);
        message = parseException(false, "E120W120");
        assertEquals("already set direction once, cannot add direction: W when parsing <E120W120>", message);
        message = parseException(false, "E");
        assertEquals("end of field without any number seen when parsing <E>", message);
        message = parseException(false, "");
        assertEquals("end of field without any number seen when parsing <>", message);
        message = parseException(false, "NW25");
        assertEquals("already set direction once, cannot add direction: W when parsing <NW25>", message);
        message = parseException(false, "N16.25\u00B0");
        assertEquals("cannot have fractional degrees before degrees sign when parsing <N16.25\u00B0>", message);
        message = parseException(false, "N16\u00B022.40'");
        assertEquals("cannot have fractional minutes before minutes sign when parsing <N16\u00B022.40'>", message);
        message = parseException(false, "");
        assertEquals("end of field without any number seen when parsing <>", message);
        message = parseException(false, "Yahoo!");
        assertEquals("invalid character: Y when parsing <Yahoo!>", message);
        message = parseException(false, "N63O025.105");
        assertEquals("invalid character: O when parsing <N63O025.105>", message);
    }

}
