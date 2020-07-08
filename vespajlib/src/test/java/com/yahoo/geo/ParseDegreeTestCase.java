// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.geo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the ParseDegree class.
 *
 * @author arnej27959
 */
public class ParseDegreeTestCase {

    private static final double delta = 0.000000000001;

    private ParseDegree parser;

    private void checkLat(boolean ans, String to_parse, double expected) {
        parser = new ParseDegree(ans, to_parse);
        assertEquals(expected, parser.latitude, delta);
        assertTrue(parser.foundLatitude);
        assertFalse(parser.foundLongitude);
    }
    private void checkLon(boolean ans, String to_parse, double expected) {
        parser = new ParseDegree(ans, to_parse);
        assertEquals(expected, parser.longitude, delta);
        assertFalse(parser.foundLatitude);
        assertTrue(parser.foundLongitude);
    }
    private void checkLat(String to_parse, double expected) {
        checkLat(true, to_parse, expected);
        checkLat(false, to_parse, expected);
    }
    private void checkLon(String to_parse, double expected) {
        checkLon(true, to_parse, expected);
        checkLon(false, to_parse, expected);
    }

    private void checkZeroLat(boolean ans, String to_parse) {
        checkLat(ans, to_parse, 0d);
    }

    private void checkZeroLon(boolean ans, String to_parse) {
        checkLon(ans, to_parse, 0d);
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

    /**
     * Tests inputs that are above latitude 90/-90 degrees and longitude 180/-180 degrees.
     */
    @Test
    public void testAboveBoundary() {
        String message = "";
        try {
            parser = new ParseDegree(false, "N90.0001");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("out of range [-90,+90]: 90.0001", message);
        try {
            parser = new ParseDegree(false, "S90.0001");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("out of range [-90,+90]: -90.0001", message);
        try {
            parser = new ParseDegree(true, "E180.0001");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("out of range [-180,+180]: 180.0001", message);
        try {
            parser = new ParseDegree(true, "W180.0001");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("out of range [-180,+180]: -180.0001", message);
        try {
            parser = new ParseDegree(false, "N90.000001");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("out of range [-90,+90]: 90.000001", message);
        try {
            parser = new ParseDegree(false, "S90.000001");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("out of range [-90,+90]: -90.000001", message);
        try {
            parser = new ParseDegree(true, "E180.000001");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("out of range [-180,+180]: 180.000001", message);
        try {
            parser = new ParseDegree(true, "W180.000001");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("out of range [-180,+180]: -180.000001", message);
    }

    /**
     * Tests various inputs that contain syntax errors.
     */
    @Test
    public void testInputErrors() {
        String message = "";
        try {
            parser = new ParseDegree(false, "N90S90");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("already set direction once, cannot add direction: S", message);
        try {
            parser = new ParseDegree(false, "E120W120");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("already set direction once, cannot add direction: W", message);
        try {
            parser = new ParseDegree(false, "E");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("end of field without any number seen", message);
        try {
            parser = new ParseDegree(false, "");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("end of field without any number seen", message);
        try {
            parser = new ParseDegree(false, "NW25");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("already set direction once, cannot add direction: W", message);
        try {
            parser = new ParseDegree(false, "N16.25\u00B0");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("cannot have fractional degrees before degrees sign", message);
        try {
            parser = new ParseDegree(false, "N16\u00B022.40'");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("cannot have fractional minutes before minutes sign", message);
        try {
            parser = new ParseDegree(false, "");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("end of field without any number seen", message);
        try {
            parser = new ParseDegree(false, "Yahoo!");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("invalid character: Y", message);
        try {
            parser = new ParseDegree(false, "N63O025.105");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("invalid character: O", message);
    }

}
