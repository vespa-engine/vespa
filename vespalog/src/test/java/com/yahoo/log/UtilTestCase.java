// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author  Bjorn Borud
 */
@SuppressWarnings("removal")
public class UtilTestCase {

    /**
     * Just make sure the static getHostName() method returns something
     * that looks half sensible.
     */
    @Test
    public void testSimple () {
        String name = Util.getHostName();
        assertNotNull(name);
    }
}
