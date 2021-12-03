// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import org.fusesource.jansi.Ansi;
import org.junit.jupiter.api.Test;

import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 */
class HtmlLoggerTest {

    @Test
    void testConversionToHtml() {
        LogRecord html = new HtmlLogger().toLog(Ansi.ansi().fg(Ansi.Color.RED).a("</body>Hello!").reset().toString());
        assertEquals("html", html.getLevel().getName());
        assertEquals("<span style=\"color: red;\">&lt;/body&gt;Hello!</span>",
                     html.getMessage());
    }

}
