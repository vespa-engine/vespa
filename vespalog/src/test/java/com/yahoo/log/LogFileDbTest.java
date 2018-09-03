// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import java.io.File;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import org.junit.Test;

/**
 * @author arnej
 */
public class LogFileDbTest {

    @Test
    public void canSave() {
        System.err.println("VH: "+System.getenv("VESPA_HOME"));
        File dir = new File(getDefaults().underVespaHome(LogFileDb.DBDIR));
        dir.mkdirs();
        if (dir.isDirectory()) {
            System.err.println("using directory: "+dir);
            new File(getDefaults().underVespaHome("logs/extra")).mkdirs();
            String fn = getDefaults().underVespaHome("logs/extra/foo-bar.log");
            LogFileDb.nowLoggingTo(fn);
            fn = getDefaults().underVespaHome("logs/extra/stamped-1.log");
            LogFileDb.nowLoggingTo(fn);
        } else {
            System.err.println("cannot create directory: "+dir);
        }
    }
}
