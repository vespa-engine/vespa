// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import java.io.IOException;

import com.yahoo.schema.parser.ParseException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests that search definitions are parsed correctly and that correct line number is reported in
 * error message.
 *
 * @author hmusum
 */
public class SchemaParsingTestCase extends AbstractSchemaTestCase {

    @Test
    void requireThatIndexingExpressionsCanBeParsed() throws Exception {
        assertNotNull(ApplicationBuilder.buildFromFile("src/test/examples/simple.sd"));
    }

    @Test
    void requireThatParseExceptionPositionIsCorrect() throws Exception {
        try {
            ApplicationBuilder.buildFromFile("src/test/examples/invalid_sd_construct.sd");
        } catch (ParseException e) {
            if (!e.getMessage().contains("at line 5, column 36.")) {
                throw e;
            }
        }
    }

    @Test
    void requireThatParserHandlesLexicalError() throws Exception {
        try {
            ApplicationBuilder.buildFromFile("src/test/examples/invalid_sd_lexical_error.sd");
        } catch (ParseException e) {
            if (!e.getMessage().contains("at line 7, column 27.")) {
                throw e;
            }
        }
    }

    @Test
    void requireErrorWhenJunkAfterSearchBlock() throws IOException, ParseException {
        try {
            ApplicationBuilder.buildFromFile("src/test/examples/invalid_sd_junk_at_end.sd");
            fail("Illegal junk at end of SD passed");
        } catch (ParseException e) {
            if (!e.getMessage().contains("at line 10, column 1")) {
                throw e;
            }
        }
    }

    @Test
    void requireErrorWhenMissingClosingSearchBracket() throws IOException, ParseException {
        try {
            ApplicationBuilder.buildFromFile("src/test/examples/invalid_sd_no_closing_bracket.sd");
            fail("SD without closing bracket passed");
        } catch (ParseException e) {
            if (!e.getMessage().contains("Encountered \"<EOF>\" at line 8, column 1")) {
                throw e;
            }
        }
    }

    @Test
    void illegalSearchDefinitionName() throws IOException, ParseException {
        try {
            ApplicationBuilder.buildFromFile("src/test/examples/invalid-name.sd");
            fail("Name with dash passed");
        } catch (ParseException e) {
            if (!e.getMessage().contains("invalid-name")) {
                throw e;
            }
        }
    }

}
