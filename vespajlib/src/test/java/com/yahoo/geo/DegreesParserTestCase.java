// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.geo;

/**
 * Tests for the DegreesParser class.
 *
 * @author <a href="mailto:gunnarga@yahoo-inc.com">Gunnar Gauslaa Bergem</a>
 */
public class DegreesParserTestCase extends junit.framework.TestCase {

    private DegreesParser parser;

    public DegreesParserTestCase(String name) {
        super(name);
    }

    /**
     * Tests different inputs that should all produce 0 or -0.
     */
    public void testZero() {
        parser = new DegreesParser("N0;E0");
        assertEquals(0d, parser.latitude);
        assertEquals(0d, parser.longitude);
        parser = new DegreesParser("S0;W0");
        assertEquals(-0d, parser.latitude);
        assertEquals(-0d, parser.longitude);
        parser = new DegreesParser("N0.0;E0.0");
        assertEquals(0d, parser.latitude);
        assertEquals(0d, parser.longitude);
        parser = new DegreesParser("S0.0;W0.0");
        assertEquals(-0d, parser.latitude);
        assertEquals(-0d, parser.longitude);
        parser = new DegreesParser("N0\u00B00'0;E0\u00B00'0");
        assertEquals(0d, parser.latitude);
        assertEquals(0d, parser.longitude);
        parser = new DegreesParser("S0\u00B00'0;W0\u00B00'0");
        assertEquals(-0d, parser.latitude);
        assertEquals(-0d, parser.longitude);
        parser = new DegreesParser("S0o0'0;W0o0'0");
        assertEquals(-0d, parser.latitude);
        assertEquals(-0d, parser.longitude);
    }

    /**
     * Tests various legal inputs and print the output
     */
    public void testPrint() {
        String here = "63N025.105;010E25.982";
        parser = new DegreesParser(here);
        System.out.println(here+" -> "+parser.latitude+"/"+parser.longitude+" (lat/long)");

        here = "N63.418417 E10.433033";
        parser = new DegreesParser(here);
        System.out.println(here+" -> "+parser.latitude+"/"+parser.longitude+" (lat/long)");

        here = "N63o025.105;E010o25.982";
        parser = new DegreesParser(here);
        System.out.println(here+" -> "+parser.latitude+"/"+parser.longitude+" (lat/long)");

        here = "N63.418417;E10.433033";
        parser = new DegreesParser(here);
        System.out.println(here+" -> "+parser.latitude+"/"+parser.longitude+" (lat/long)");

        here = "63.418417N;10.433033E";
        parser = new DegreesParser(here);
        System.out.println(here+" -> "+parser.latitude+"/"+parser.longitude+" (lat/long)");

        here = "N37.417075;W122.025358";
        parser = new DegreesParser(here);
        System.out.println(here+" -> "+parser.latitude+"/"+parser.longitude+" (lat/long)");

        here = "N37\u00B024.983;W122\u00B001.481";
        parser = new DegreesParser(here);
        System.out.println(here+" -> "+parser.latitude+"/"+parser.longitude+" (lat/long)");
    }

    /**
     * Tests inputs that are close to 0.
     */
    public void testNearZero() {
        parser = new DegreesParser("N0.0001;E0.0001");
        assertEquals(0.0001, parser.latitude);
        assertEquals(0.0001, parser.longitude);
        parser = new DegreesParser("S0.0001;W0.0001");
        assertEquals(-0.0001, parser.latitude);
        assertEquals(-0.0001, parser.longitude);

        parser = new DegreesParser("N0.000001;E0.000001");
        assertEquals(0.000001, parser.latitude);
        assertEquals(0.000001, parser.longitude);
        parser = new DegreesParser("S0.000001;W0.000001");
        assertEquals(-0.000001, parser.latitude);
        assertEquals(-0.000001, parser.longitude);

        parser = new DegreesParser("N0\u00B00'1;E0\u00B00'1");
        assertEquals(1/3600d, parser.latitude);
        assertEquals(1/3600d, parser.longitude);
        parser = new DegreesParser("S0\u00B00'1;W0\u00B00'1");
        assertEquals(-1/3600d, parser.latitude);
        assertEquals(-1/3600d, parser.longitude);
    }

    /**
     * Tests inputs that are close to latitude 90/-90 degrees and longitude 180/-180 degrees.
     */
    public void testNearBoundary() {

        parser = new DegreesParser("N89.9999;E179.9999");
        assertEquals(89.9999, parser.latitude);
        assertEquals(179.9999, parser.longitude);
        parser = new DegreesParser("S89.9999;W179.9999");
        assertEquals(-89.9999, parser.latitude);
        assertEquals(-179.9999, parser.longitude);

        parser = new DegreesParser("N89.999999;E179.999999");
        assertEquals(89.999999, parser.latitude);
        assertEquals(179.999999, parser.longitude);
        parser = new DegreesParser("S89.999999;W179.999999");
        assertEquals(-89.999999, parser.latitude);
        assertEquals(-179.999999, parser.longitude);

        parser = new DegreesParser("N89\u00B059'59;E179\u00B059'59");
        assertEquals(89+59/60d+59/3600d, parser.latitude);
        assertEquals(179+59/60d+59/3600d, parser.longitude);
        parser = new DegreesParser("S89\u00B059'59;W179\u00B059'59");
        assertEquals(-(89+59/60d+59/3600d), parser.latitude);
        assertEquals(-(179+59/60d+59/3600d), parser.longitude);
    }

    /**
     * Tests inputs that are on latitude 90/-90 degrees and longitude 180/-180 degrees.
     */
    public void testOnBoundary() {
        parser = new DegreesParser("N90;E180");
        assertEquals(90d, parser.latitude);
        assertEquals(180d, parser.longitude);
        parser = new DegreesParser("S90;W180");
        assertEquals(-90d, parser.latitude);
        assertEquals(-180d, parser.longitude);

        parser = new DegreesParser("N90\u00B00'0;E180\u00B00'0");
        assertEquals(90d, parser.latitude);
        assertEquals(180d, parser.longitude);
        parser = new DegreesParser("S90\u00B00'0;W180\u00B00'0");
        assertEquals(-90d, parser.latitude);
        assertEquals(-180d, parser.longitude);
    }

    /**
     * Tests inputs that are above latitude 90/-90 degrees and longitude 180/-180 degrees.
      */
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
