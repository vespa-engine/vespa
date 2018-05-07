// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.geo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for the DegreesParser class.
 *
 * @author Gunnar Gauslaa Bergem
 */
public class DegreesParserTestCase {

    private static final double delta = 0.000000000001;

    private DegreesParser parser;

    /**
     * Tests different inputs that should all produce 0 or -0.
     */
    @Test
    public void testZero() {
        parser = new DegreesParser("N0;E0");
        assertEquals(0d, parser.latitude, delta);
        assertEquals(0d, parser.longitude, delta);
        parser = new DegreesParser("S0;W0");
        assertEquals(-0d, parser.latitude, delta);
        assertEquals(-0d, parser.longitude, delta);
        parser = new DegreesParser("N0.0;E0.0");
        assertEquals(0d, parser.latitude, delta);
        assertEquals(0d, parser.longitude, delta);
        parser = new DegreesParser("S0.0;W0.0");
        assertEquals(-0d, parser.latitude, delta);
        assertEquals(-0d, parser.longitude, delta);
        parser = new DegreesParser("N0\u00B00'0;E0\u00B00'0");
        assertEquals(0d, parser.latitude, delta);
        assertEquals(0d, parser.longitude, delta);
        parser = new DegreesParser("S0\u00B00'0;W0\u00B00'0");
        assertEquals(-0d, parser.latitude, delta);
        assertEquals(-0d, parser.longitude, delta);
        parser = new DegreesParser("S0o0'0;W0o0'0");
        assertEquals(-0d, parser.latitude, delta);
        assertEquals(-0d, parser.longitude, delta);
    }

    /**
     * Tests inputs that are close to 0.
     */
    @Test
    public void testNearZero() {
        parser = new DegreesParser("N0.0001;E0.0001");
        assertEquals(0.0001, parser.latitude, delta);
        assertEquals(0.0001, parser.longitude, delta);
        parser = new DegreesParser("S0.0001;W0.0001");
        assertEquals(-0.0001, parser.latitude, delta);
        assertEquals(-0.0001, parser.longitude, delta);

        parser = new DegreesParser("N0.000001;E0.000001");
        assertEquals(0.000001, parser.latitude, delta);
        assertEquals(0.000001, parser.longitude, delta);
        parser = new DegreesParser("S0.000001;W0.000001");
        assertEquals(-0.000001, parser.latitude, delta);
        assertEquals(-0.000001, parser.longitude, delta);

        parser = new DegreesParser("N0\u00B00'1;E0\u00B00'1");
        assertEquals(1/3600d, parser.latitude, delta);
        assertEquals(1/3600d, parser.longitude, delta);
        parser = new DegreesParser("S0\u00B00'1;W0\u00B00'1");
        assertEquals(-1/3600d, parser.latitude, delta);
        assertEquals(-1/3600d, parser.longitude, delta);
    }

    /**
     * Tests inputs that are close to latitude 90/-90 degrees and longitude 180/-180 degrees.
     */
    @Test
    public void testNearBoundary() {
        parser = new DegreesParser("N89.9999;E179.9999");
        assertEquals(89.9999, parser.latitude, delta);
        assertEquals(179.9999, parser.longitude, delta);
        parser = new DegreesParser("S89.9999;W179.9999");
        assertEquals(-89.9999, parser.latitude, delta);
        assertEquals(-179.9999, parser.longitude, delta);

        parser = new DegreesParser("N89.999999;E179.999999");
        assertEquals(89.999999, parser.latitude, delta);
        assertEquals(179.999999, parser.longitude, delta);
        parser = new DegreesParser("S89.999999;W179.999999");
        assertEquals(-89.999999, parser.latitude, delta);
        assertEquals(-179.999999, parser.longitude, delta);

        parser = new DegreesParser("N89\u00B059'59;E179\u00B059'59");
        assertEquals(89+59/60d+59/3600d, parser.latitude, delta);
        assertEquals(179+59/60d+59/3600d, parser.longitude, delta);
        parser = new DegreesParser("S89\u00B059'59;W179\u00B059'59");
        assertEquals(-(89+59/60d+59/3600d), parser.latitude, delta);
        assertEquals(-(179+59/60d+59/3600d), parser.longitude, delta);
    }

    /**
     * Tests inputs that are on latitude 90/-90 degrees and longitude 180/-180 degrees.
     */
    @Test
    public void testOnBoundary() {
        parser = new DegreesParser("N90;E180");
        assertEquals(90d, parser.latitude, delta);
        assertEquals(180d, parser.longitude, delta);
        parser = new DegreesParser("S90;W180");
        assertEquals(-90d, parser.latitude, delta);
        assertEquals(-180d, parser.longitude, delta);

        parser = new DegreesParser("N90\u00B00'0;E180\u00B00'0");
        assertEquals(90d, parser.latitude, delta);
        assertEquals(180d, parser.longitude, delta);
        parser = new DegreesParser("S90\u00B00'0;W180\u00B00'0");
        assertEquals(-90d, parser.latitude, delta);
        assertEquals(-180d, parser.longitude, delta);
    }

    /**
     * Tests inputs that are above latitude 90/-90 degrees and longitude 180/-180 degrees.
     */
    @Test
    public void testAboveBoundary() {
        String message = "";
        try {
            parser = new DegreesParser("N90.0001;E0");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("out of range [-90,+90]: 90.0001", message);
        try {
            parser = new DegreesParser("S90.0001;E0");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("out of range [-90,+90]: -90.0001", message);
        try {
            parser = new DegreesParser("N0;E180.0001");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("out of range [-180,+180]: 180.0001", message);
        try {
            parser = new DegreesParser("N0;W180.0001");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("out of range [-180,+180]: -180.0001", message);
        try {
            parser = new DegreesParser("N90.000001;E0");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("out of range [-90,+90]: 90.000001", message);
        try {
            parser = new DegreesParser("S90.000001;E0");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("out of range [-90,+90]: -90.000001", message);
        try {
            parser = new DegreesParser("N0;E180.000001");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("out of range [-180,+180]: 180.000001", message);
        try {
            parser = new DegreesParser("N0;W180.000001");
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
            parser = new DegreesParser("N90;S90");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("found latitude (N or S) twice", message);
        try {
            parser = new DegreesParser("E120;W120");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("found longitude (E or W) twice", message);
        try {
            parser = new DegreesParser("N90;90");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("end of field without any compass direction seen", message);
        try {
            parser = new DegreesParser("N90;E");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("end of field without any number seen", message);
        try {
            parser = new DegreesParser(";");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("end of field without any compass direction seen", message);
        try {
            parser = new DegreesParser("25;60");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("end of field without any compass direction seen", message);
        try {
            parser = new DegreesParser("NW25;SW60");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("already set direction once, cannot add direction: W", message);
        try {
            parser = new DegreesParser("N16.25\u00B0;W60");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("cannot have fractional degrees before degrees sign", message);
        try {
            parser = new DegreesParser("N16\u00B022.40';W60");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("cannot have fractional minutes before minutes sign", message);
        try {
            parser = new DegreesParser("");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("end of field without any compass direction seen", message);
        try {
            parser = new DegreesParser("Yahoo!");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("invalid character: Y", message);
        try {
            parser = new DegreesParser("N63O025.105;E010O25.982");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("invalid character: O", message);
    }

}
