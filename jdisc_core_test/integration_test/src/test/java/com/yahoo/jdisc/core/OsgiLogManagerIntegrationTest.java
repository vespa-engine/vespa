// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


/**
 * @author Simon Thoresen Hult
 */
public class OsgiLogManagerIntegrationTest {

    @Test
    public void requireThatRootLoggerLevelIsNotModifiedIfLoggerConfigIsGiven() {
        Logger logger = Logger.getLogger("");
        logger.setLevel(Level.WARNING);

        OsgiLogManager.newInstance().install(Mockito.mock(BundleContext.class));

        assertNotNull(System.getProperty("java.util.logging.config.file"));
        assertEquals(Level.WARNING, logger.getLevel());
    }
}
