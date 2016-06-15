// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.test;

import com.yahoo.prelude.Location;

/**
 * Tests the Location class. Currently does not test all "features" of Location class.
 *
 * @author  <a href="mailto:einarmr@yahoo-inc.com">Einar M. R. Rosenvinge</a>
 */
public class LocationTestCase extends junit.framework.TestCase {

    public LocationTestCase (String name) {
        super(name);
    }

    public void testAspect() {
        //0 degrees latitude, on the equator
        Location loc1 = new Location("[2,-1110000,330000,-1160000,340000](2,-1100222,0,300,0,1,0,CalcLatLon)");
        assertEquals(loc1.toString(), "[2,-1110000,330000,-1160000,340000](2,-1100222,0,300,0,1,0,4294967295)");

        //90 degrees latitude, on the north pole
        Location loc2 = new Location("[2,-1110000,330000,-1160000,340000](2,-1100222,90000000,300,0,1,0,CalcLatLon)");
        assertEquals(loc2.toString(), "[2,-1110000,330000,-1160000,340000](2,-1100222,90000000,300,0,1,0)");

        Location loc3 = new Location("attr1:[2,-1110000,330000,-1160000,340000](2,-1100222,0,300,0,1,0,CalcLatLon)");
        assertEquals(loc3.toString(), "attr1:[2,-1110000,330000,-1160000,340000](2,-1100222,0,300,0,1,0,4294967295)");
    }

}
