// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import org.fusesource.jansi.Ansi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 */
class HtmlLoggerTest {

    @Test
    void testConversionToHtml() {
        String splitMessage = Ansi.ansi().fg(Ansi.Color.RED).a("</body>Hello!\ncontinued").reset().toString();
        List<String> messages = List.of(splitMessage.split("\n"));
        LogRecord html0 = new HtmlLogger().toLog(messages.get(0));
        assertEquals("html", html0.getLevel().getName());
        assertEquals("<span style=\"color: red;\">&lt;/body&gt;Hello!</span>",
                     html0.getMessage());

        LogRecord html1 = new HtmlLogger().toLog(messages.get(1));
        assertEquals("html", html1.getLevel().getName());
        assertEquals("continued",
                     html1.getMessage());
    }

}
