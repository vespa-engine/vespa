// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.java7compat;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author baldersheim
 * @since 5.2
 */

public class UtilTest {

    @Test
    public void requireJava7CompatibleDoublePrinting() {
        if (Util.isJava7Compatible()) {
            assertEquals("0.004", String.valueOf(0.0040));
        }else {
            assertEquals("0.0040", String.valueOf(0.0040));
        }
        assertEquals("0.004", Util.toJava7String(0.0040) );
    }
    
    @Test
    public void nonCompatible() {
        assertEquals(Util.nonJava7CompatibleString("0.0040"), "0.004");
    }
    
}
