// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers.archive;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.logging.Logger;

import java.util.logging.Level;

/**
 * This class is not thread-safe.
 *
 * @author Bjorn Borud
 */
public class LogWriter {
    private static final Logger log = Logger.getLogger(LogWriter.class.getName());

    private long bytesWritten = 0;
    private int generation;
    private int maxSize = 20 * (1024 * 1024);
    private final int resumeLimit = 95;
    private final int resumeLimitSize = (maxSize * resumeLimit / 100);
    private File currentFile;
    private Writer writer;
    private final String prefix;
    private final FilesArchived archive;

    public LogWriter(String prefix, int maxSize, FilesArchived archive) throws IOException {
        this.prefix = prefix;
        this.maxSize = maxSize;
        this.archive = archive;
        this.generation = archive.highestGen(prefix);
        writer = nextWriter();
        archive.triggerMaintenance();
    }

    /**
     * This is called when we want to rotate the output file to
     * start writing the next file.  There are two scenarios when
     * we do this:
     * <UL>
     * <LI> initial case, when we have no file
     * <LI> when we have filled the file and want to rotate it
     * </UL>
     */
    private Writer nextWriter() throws IOException {
        close();
        int maxAttempts = 1000;
        while (maxAttempts-- > 0) {
            String name = prefix + "-" + generation++;
            File f = new File(name);

            // make sure directory exists
            File dir = f.getParentFile();
            if (! dir.exists()) {
                dir.mkdirs();
            }

            // if compressed version exists we skip it
            if ((new File(name + ".gz").exists())
                    || (new File(name + ".bz2").exists())) {
                continue;
            }

            // if file does not exist we have a winner
            if (! f.exists()) {
                log.log(Level.FINE, () -> "nextWriter, new file: " + name);
                currentFile = f;
                bytesWritten = 0;
                return new FileWriter(f, true);
            }

            // just skip over directories for now
            if (! f.isFile()) {
                log.fine("nextWriter, " + name + " is a directory, skipping");
                continue;
            }

            // if the size is < resumeSizeLimit then we open it
            if (f.length() < resumeLimitSize) {
                log.fine("nextWriter, resuming " + name + ", length was " + f.length());
                currentFile = f;
                bytesWritten = f.length();
                return new FileWriter(f, true);
            } else {

                log.fine("nextWriter, not resuming " + name
                                 + " because it is bigger than "
                                 + resumeLimit
                                 + " percent of max");
            }
        }

        throw new RuntimeException("Unable to create next log file");
    }

    public void write(String str) throws IOException {
        if (writer == null) {
            writer = nextWriter();
            archive.triggerMaintenance();
        }

        bytesWritten += str.length();
        writer.write(str, 0, str.length());

        if (bytesWritten >= maxSize) {
            log.fine("logfile '"
                             + currentFile.getAbsolutePath()
                             + "' full, rotating");
            writer = nextWriter();
            archive.triggerMaintenance();
        }
    }


    public synchronized void flush() throws IOException {
        if (writer != null) {
            writer.flush();
        }
    }

    public synchronized void close() throws IOException {
        if (writer != null) {
            writer.flush();
            writer.close();
            writer = null;
        }
    }
}
