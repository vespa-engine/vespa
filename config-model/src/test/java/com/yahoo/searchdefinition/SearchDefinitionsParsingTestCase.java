// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.yahoo.searchdefinition.parser.ParseException;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests that search definitions are parsed correctly and that correct line number is reported in
 * error message.
 *
 * @author hmusum
 */
public class SearchDefinitionsParsingTestCase extends SearchDefinitionTestCase {

    @Test
    public void requireThatIndexingExpressionsCanBeParsed() throws Exception {
        assertNotNull(SearchBuilder.buildFromFile("src/test/examples/simple.sd"));
    }

    @Test
    public void requireThatParseExceptionPositionIsCorrect() throws Exception {
        try {
            SearchBuilder.buildFromFile("src/test/examples/invalid_sd_construct.sd");
        } catch (ParseException e) {
            if ( ! e.getMessage().contains("at line 5, column 36.")) {
                throw e;
            }
        }
    }

    @Test
    public void requireThatParserHandlesLexicalError() throws Exception {
        try {
            SearchBuilder.buildFromFile("src/test/examples/invalid_sd_lexical_error.sd");
        } catch (ParseException e) {
            if (!e.getMessage().contains("at line 7, column 27.")) {
                throw e;
            }
        }
    }

    @Test
    public void requireErrorWhenJunkAfterSearchBlock() throws IOException, ParseException {
        try {
            SearchBuilder.buildFromFile("src/test/examples/invalid_sd_junk_at_end.sd");
            fail("Illegal junk at end of SD passed");
        } catch (ParseException e) {
            if (!e.getMessage().contains("at line 10, column 1")) {
                throw e;
            }
        }
    }

    @Test
    public void requireErrorWhenMissingClosingSearchBracket() throws IOException, ParseException {
        try {
            SearchBuilder.buildFromFile("src/test/examples/invalid_sd_no_closing_bracket.sd");
            fail("SD without closing bracket passed");
        } catch (ParseException e) {
            if (!e.getMessage().contains("Encountered \"<EOF>\" at line 8, column 1")) {
                throw e;
            }
        }
    }

    private static class WarningCatcher extends Handler {
        volatile boolean gotYqlWarning = false;

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel() == Level.WARNING && record.getMessage().indexOf("YQL") >= 0) {
                gotYqlWarning = true;
            }
        }

        @Override
        public void flush() {
            // intentionally left blank
        }

        @Override
        public void close() throws SecurityException {
            // intentionally left blank
        }
    }


    @Test
    public void requireYqlCompatibilityIsTested() throws Exception {
        Logger log = Logger.getLogger("DeployLogger");
        WarningCatcher w = new WarningCatcher();
        log.addHandler(w);
        assertNotNull(SearchBuilder.buildFromFile("src/test/examples/simple-with-weird-name.sd"));
        log.removeHandler(w);
        assertTrue(w.gotYqlWarning);
    }
}
