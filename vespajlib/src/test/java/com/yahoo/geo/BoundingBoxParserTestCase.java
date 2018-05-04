// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.geo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for the BoundingBoxParser class.
 *
 * @author arnej27959
 */
public class BoundingBoxParserTestCase {

    private static final double delta = 0.000000000001;

    private BoundingBoxParser parser;

    private void allZero(BoundingBoxParser data) {
        assertEquals(0d, data.n, delta);
        assertEquals(0d, data.s, delta);
        assertEquals(0d, data.e, delta);
        assertEquals(0d, data.w, delta);
    }

    private void all1234(BoundingBoxParser data) {
        assertEquals(1d, data.n, delta);
        assertEquals(2d, data.s, delta);
        assertEquals(3d, data.e, delta);
        assertEquals(4d, data.w, delta);
    }

    /**
     * Tests different inputs that should all produce 0
     */
    @Test
    public void testZero() {
        parser = new BoundingBoxParser("n=0,s=0,e=0,w=0");
        allZero(parser);
        parser = new BoundingBoxParser("N=0,S=0,E=0,W=0");
        allZero(parser);
        parser = new BoundingBoxParser("NORTH=0,SOUTH=0,EAST=0,WEST=0");
        allZero(parser);
        parser = new BoundingBoxParser("north=0,south=0,east=0,west=0");
        allZero(parser);
        parser = new BoundingBoxParser("n=0.0,s=0.0e-17,e=0.0e0,w=0.0e100");
        allZero(parser);
        parser = new BoundingBoxParser("s:0.0,w:0.0,n:0.0,e:0.0");
        allZero(parser);
        parser = new BoundingBoxParser("s:0.0,w:0.0,n:0.0,e:0.0");
        allZero(parser);
    }

    @Test
    public void testOneTwoThreeFour() {
        parser = new BoundingBoxParser("n=1,s=2,e=3,w=4");
        all1234(parser);
        parser = new BoundingBoxParser("n=1.0,s=2.0,e=3.0,w=4.0");
        all1234(parser);
        parser = new BoundingBoxParser("s=2,w=4,n=1,e=3");
        all1234(parser);
        parser = new BoundingBoxParser("N=1,S=2,E=3,W=4");
        all1234(parser);
        parser = new BoundingBoxParser("S=2,W=4,N=1,E=3");
        all1234(parser);
        parser = new BoundingBoxParser("north=1.0,south=2.0,east=3.0,west=4.0");
        all1234(parser);
        parser = new BoundingBoxParser("South=2.0 West=4.0 North=1.0 East=3.0");
        all1234(parser);
    }

    /**
     * Tests various legal inputs and print the output
     */
    @Test
    public void testPrint() {
        String here = "n=63.418417 E=10.433033 S=37.7 W=-122.02";
        parser = new BoundingBoxParser(here);
        System.out.println(here+" -> "+parser);
    }

    @Test
    public void testGeoPlanetExample() {
        /* example XML:
           <boundingBox>  
               <southWest>  
                   <latitude>40.183868</latitude>  
                   <longitude>-74.819519</longitude>  
               </southWest>  
               <northEast>  
                   <latitude>40.248291</latitude>  
                   <longitude>-74.728798</longitude>  
               </northEast>  
           </boundingBox>  

           can be input as:

           s=40.183868,w=-74.819519,n=40.248291,e=-74.728798
        */
        parser = new BoundingBoxParser("south=40.183868,west=-74.819519,north=40.248291,east=-74.728798");
        assertEquals(40.183868d,  parser.s, 0.0000001);
        assertEquals(-74.819519d, parser.w, 0.0000001);
        assertEquals(40.248291d,  parser.n, 0.0000001);
        assertEquals(-74.728798d, parser.e, 0.0000001);
    }

    @Test
    public void testGwsExample() {
        /* example XML:
           <boundingbox>
             <north>37.44899</north><south>37.3323</south><east>-121.98241</east><west>-122.06566</west>
           </boundingbox>
           can be input as: north:37.44899 south:37.3323, east:-121.98241 west:-122.06566
        */
        parser = new BoundingBoxParser(" north:37.44899 south:37.3323, east:-121.98241 west:-122.06566 ");
        assertEquals(37.44899d,   parser.n, 0.000001);
        assertEquals(37.33230d,   parser.s, 0.000001);
        assertEquals(-121.98241d, parser.e, 0.000001);
        assertEquals(-122.06566d, parser.w, 0.000001);
    }

    /**
     * Tests various inputs that contain syntax errors.
     */
    @Test
    public void testInputErrors() {
        String message = "";
        try {
            parser = new BoundingBoxParser("n=10.11,e=2.02");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("Missing bounding box limits, n=true s=false e=true w=false", message);

        try {
            parser = new BoundingBoxParser("n=11.01,s=10.11,e=xyzzy,w=-122.2");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("Could not parse e limit 'xyzzy' as a number", message);

        try {
            parser = new BoundingBoxParser("n=11.01,n=10.11,e=-122.0,w=-122.2");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("multiple limits for 'n' boundary", message);

        try {
            parser = new BoundingBoxParser("s=11.01,s=10.11,e=-122.0,w=-122.2");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("multiple limits for 's' boundary", message);

        try {
            parser = new BoundingBoxParser("n=11.01,s=10.11,e=-122.0,e=-122.2");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("multiple limits for 'e' boundary", message);

        try {
            parser = new BoundingBoxParser("n=11.01,s=10.11,w=-122.0,w=-122.2");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        assertEquals("multiple limits for 'w' boundary", message);
    }

}
