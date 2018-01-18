// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.commons.parser;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author sgrostad
 * @author olaaun
 */

public class OutputParserTest {

    private static final String RETURN_VALUE = "#returnValue#";
    private static final String OUTPUT_WITH_MATCH_1 = "This; Should be; a match; when; Parsing ; " + RETURN_VALUE;
    private static final String OUTPUT_WITHOUT_MATCH = "This; is; not a; match";
    private static final String OUTPUT_WITH_MATCH_2 = "But; thiS will-also; be; a match:; this ; " + RETURN_VALUE;
    private static final String SEARCH_WORD_1 = "Parsing";
    private static final String SEARCH_WORD_2 = "this";
    private static final String REGEX_SEARCH_WORD = ".*S.*";
    private List<String> commandOutput;
    private List<String> searchWords;

    @Before
    public void setup() {
        commandOutput = new ArrayList<>(Arrays.asList(OUTPUT_WITH_MATCH_1, OUTPUT_WITHOUT_MATCH, OUTPUT_WITH_MATCH_2));
    }

    @Test
    public void parseOutput_searching_for_two_normal_words() {
        searchWords = new ArrayList<>(Arrays.asList(SEARCH_WORD_1, SEARCH_WORD_2));
        ParseInstructions parseInstructions = new ParseInstructions(6, 8, " ", searchWords);
        List<ParseResult> parseResults = OutputParser.parseOutput(parseInstructions, commandOutput);
        ParseResult expectedParseResult1 = new ParseResult(SEARCH_WORD_1, RETURN_VALUE);
        ParseResult expectedParseResult2 = new ParseResult(SEARCH_WORD_2, RETURN_VALUE);
        assertEquals(expectedParseResult1, parseResults.get(0));
        assertEquals(expectedParseResult2, parseResults.get(1));
    }

    @Test
    public void parseOutput_searching_for_two_normal_words_with_semicolon_as_line_split() {
        searchWords = new ArrayList<>(Arrays.asList(SEARCH_WORD_1, SEARCH_WORD_2));
        ParseInstructions parseInstructions = new ParseInstructions(4, 5, ";", searchWords);
        List<ParseResult> parseResults = OutputParser.parseOutput(parseInstructions, commandOutput);
        ParseResult expectedParseResult1 = new ParseResult(SEARCH_WORD_1, RETURN_VALUE);
        ParseResult expectedParseResult2 = new ParseResult(SEARCH_WORD_2, RETURN_VALUE);
        assertEquals(expectedParseResult1, parseResults.get(0));
        assertEquals(expectedParseResult2, parseResults.get(1));
    }

    @Test
    public void parseOutput_searching_for_word_containing_capital_s() {
        searchWords = Collections.singletonList(REGEX_SEARCH_WORD);
        ParseInstructions parseInstructions = new ParseInstructions(1, 8, " ", searchWords);
        List<ParseResult> parseResults = OutputParser.parseOutput(parseInstructions, commandOutput);
        ParseResult expectedParseResult1 = new ParseResult("Should", RETURN_VALUE);
        ParseResult expectedParseResult2 = new ParseResult("thiS", RETURN_VALUE);
        assertEquals(expectedParseResult1, parseResults.get(0));
        assertEquals(expectedParseResult2, parseResults.get(1));
    }

    @Test
    public void parseSingleOutput_should_return_first_match() {
        searchWords = Collections.singletonList(SEARCH_WORD_1);
        ParseInstructions parseInstructions = new ParseInstructions(6, 8, " ", searchWords);
        Optional<ParseResult> parseResult = OutputParser.parseSingleOutput(parseInstructions, commandOutput);
        ParseResult expectedParseResult = new ParseResult(SEARCH_WORD_1, RETURN_VALUE);
        assertEquals(Optional.of(expectedParseResult), parseResult);
    }

    @Test
    public void parseSingleOutput_should_return_invalid_parseResult() {
        searchWords = Collections.singletonList("No match");
        ParseInstructions parseInstructions = new ParseInstructions(6, 8, " ", searchWords);
        Optional<ParseResult> parseResult = OutputParser.parseSingleOutput(parseInstructions, commandOutput);
        assertEquals(Optional.empty(), parseResult);
    }

}
