// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import java.io.File;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;

import com.yahoo.io.IOUtils;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author arnej
 */
public class LogFileDbTest {

    @Test
    public void canSave() {
        System.err.println("VH: "+System.getenv("VESPA_HOME"));
        File dir = new File(getDefaults().underVespaHome(LogFileDb.DBDIR));
        assertTrue(!dir.exists() || IOUtils.recursiveDeleteDir(dir));
        System.err.println("using directory: "+dir);
        File extraDir = new File(getDefaults().underVespaHome("logs/extra"));
        assertTrue(!extraDir.exists() || IOUtils.recursiveDeleteDir(extraDir));
        String fn = getDefaults().underVespaHome("logs/extra/foo-bar.log");
        assertTrue(LogFileDb.nowLoggingTo(fn));
        fn = getDefaults().underVespaHome("logs/extra/stamped-1.log");
        assertTrue(LogFileDb.nowLoggingTo(fn));
    }
}
