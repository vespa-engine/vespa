// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import java.io.IOException;

import com.yahoo.searchdefinition.parser.ParseException;

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

    @Test
    public void illegalSearchDefinitionName() throws IOException, ParseException {
        try {
            SearchBuilder.buildFromFile("src/test/examples/invalid-name.sd");
            fail("Name with dash passed");
        } catch (ParseException e) {
            if ( ! e.getMessage().contains("invalid-name")) {
                throw e;
            }
        }
    }

    // TODO: Remove in Vespa 8
    @Test
    public void requireThatParserHandlesHeadAndBody() throws IOException, ParseException {
        assertNotNull(SearchBuilder.buildFromFile("src/test/examples/header_body.sd"));
    }

}
