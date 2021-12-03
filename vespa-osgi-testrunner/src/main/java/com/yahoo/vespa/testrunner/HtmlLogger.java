// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import org.fusesource.jansi.HtmlAnsiOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Converts ANSI output to HTML-safe log records
 *
 * @author jonmv
 */
public class HtmlLogger {

    public static final Level HTML = new Level("html", 1) { };

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final PrintStream formatter = new PrintStream(new HtmlAnsiOutputStream(buffer));

    public LogRecord toLog(String line) {
        if (line.length() > 1 << 13)
            line = line.substring(0, 1 << 13) + " ... (this log entry was truncated due to size)";

        buffer.reset();
        formatter.print(line);
        formatter.flush();
        return new LogRecord(HTML, buffer.toString(UTF_8)); //.replaceAll(" ", "&nbsp;"));
    }

}
