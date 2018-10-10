// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.*;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;


/**
 * @author arnej
 *
 * This class takes care of saving meta-data about a log-file,
 * ensuring that we can enact policies about log retention.
 **/
public class LogFileDb {

    static final String DBDIR = "var/db/vespa/logfiledb/";

    private static long dayStamp() {
        long s = System.currentTimeMillis() / 1000;
        return s / 100000;
    }

    private static OutputStream metaFile() throws java.io.IOException {
        File dir = new File(getDefaults().underVespaHome(DBDIR));
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                System.err.println("Failed creating logfiledb directory '" + dir.getPath() + "'.");
            }
        }
        String fn = dir + "logfiles." + dayStamp();
        Path path = Paths.get(fn);
        return Files.newOutputStream(path, CREATE, APPEND);
    }

    public static boolean nowLoggingTo(String filename) {
        if (filename.contains("\n")) {
            throw new IllegalArgumentException("Cannot use filename with newline: "+filename);
        }
        long s = System.currentTimeMillis() / 1000;
        String meta = "" + s + " " + filename + "\n";
        byte[] data = meta.getBytes(UTF_8);
        try (OutputStream out = metaFile()) {
            out.write(data);
        } catch (java.io.IOException e) {
            System.err.println("Saving meta-data about logfile "+filename+" failed: "+e);
            return false;
        }
        return true;
    }
}
