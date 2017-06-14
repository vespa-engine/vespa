// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers.archive;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.logging.Logger;

import com.yahoo.log.LogLevel;

/**
 * This class is not thread-safe.
 *
 * @author Bjorn Borud
 */
public class LogWriter extends Writer {
    private static final Logger log = Logger.getLogger(LogWriter.class.getName());

    private long bytesWritten = 0;
    private int generation = 0;
    private int maxSize = 20 * (1024 * 1024);
    private final int resumeLimit = 95;
    private final int resumeLimitSize = (maxSize * resumeLimit / 100);
    private File currentFile;
    private Writer writer;
    private final String prefix;

    public LogWriter(String prefix, int maxSize) throws IOException {
        this.prefix = prefix;
        this.maxSize = maxSize;
        writer = nextWriter();
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

        if (writer != null) {
            writer.close();
        }

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
                log.log(LogLevel.DEBUG, "nextWriter, new file: " + name);
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

    /**
     * Note that this method should not be used directly since
     * that would circumvent rotation when it grows past its
     * maximum size.  use the one that takes String instead.
     * <p>
     * <em>
     * (This is a class which is only used internally anyway)
     * </em>
     */
    public void write(char[] cbuff, int offset, int len) throws IOException {
        throw new RuntimeException("This method should not be used");
    }

    public void write(String str) throws IOException {
        if (writer == null) {
            writer = nextWriter();
        }

        bytesWritten += str.length();
        writer.write(str, 0, str.length());

        if (bytesWritten >= maxSize) {
            log.fine("logfile '"
                             + currentFile.getAbsolutePath()
                             + "' full, rotating");
            writer = nextWriter();
        }
    }


    public void flush() throws IOException {
        if (writer != null) {
            writer.flush();
        }
    }

    public void close() throws IOException {
        flush();
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }
}
