package com.yahoo.vespa.hosted.node.verification.spec.parse;

import com.yahoo.vespa.hosted.node.verification.spec.mock.MockCommandExecutor;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Created by sgrostad on 21/07/2017.
 */
public class OutputParserTest {
    private final String RETURN_VALUE = "#returnValue#";
    private final String OUTPUT_WITH_MATCH_1 = "This; Should be; a match; when; Parsing ; " + RETURN_VALUE;
    private final String OUTPUT_WITHOUT_MATCH = "This; is; not a; match";
    private final String OUTPUT_WITH_MATCH_2 = "But; thiS will-also; be; a match:; this ; " + RETURN_VALUE;
    private final String SEARCH_WORD_1 = "Parsing";
    private final String SEARCH_WORD_2 = "this";
    private final String REGEX_SEARCH_WORD = ".*S.*";
    private final String PARSE_OUTPUT_WITH_SKIPS_TEST_FILE = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/parseOutputWithSkipsTest";

    private ArrayList<String> commandOutput;
    private ArrayList<String> searchWords;

    @Before
    public void setup(){
        commandOutput = new ArrayList<>(Arrays.asList(OUTPUT_WITH_MATCH_1, OUTPUT_WITHOUT_MATCH ,OUTPUT_WITH_MATCH_2));
    }

    @Test
    public void test_parseOutput_searching_for_two_normal_words(){
        searchWords = new ArrayList<>(Arrays.asList(SEARCH_WORD_1, SEARCH_WORD_2));
        ParseInstructions parseInstructions = new ParseInstructions(6,8, " ",searchWords);
        ArrayList<ParseResult> parseResults = OutputParser.parseOutput(parseInstructions, commandOutput);
        ParseResult expectedParseResult1 = new ParseResult(SEARCH_WORD_1, RETURN_VALUE);
        ParseResult expectedParseResult2 = new ParseResult(SEARCH_WORD_2, RETURN_VALUE);
        assertEquals(expectedParseResult1, parseResults.get(0));
        assertEquals(expectedParseResult2, parseResults.get(1));
    }

    @Test
    public void test_parseOutput_searching_for_two_normal_words_with_semicolon_as_line_split(){
        searchWords = new ArrayList<>(Arrays.asList(SEARCH_WORD_1, SEARCH_WORD_2));
        ParseInstructions parseInstructions = new ParseInstructions(4,5, ";",searchWords);
        ArrayList<ParseResult> parseResults = OutputParser.parseOutput(parseInstructions, commandOutput);
        ParseResult expectedParseResult1 = new ParseResult(SEARCH_WORD_1, RETURN_VALUE);
        ParseResult expectedParseResult2 = new ParseResult(SEARCH_WORD_2, RETURN_VALUE);
        assertEquals(expectedParseResult1, parseResults.get(0));
        assertEquals(expectedParseResult2, parseResults.get(1));
    }

    @Test
    public void test_parseOutput_searching_for_word_containing_capital_s(){
        searchWords = new ArrayList<>(Arrays.asList(REGEX_SEARCH_WORD));
        ParseInstructions parseInstructions = new ParseInstructions(1,8, " ",searchWords);
        ArrayList<ParseResult> parseResults = OutputParser.parseOutput(parseInstructions, commandOutput);
        ParseResult expectedParseResult1 = new ParseResult("Should", RETURN_VALUE);
        ParseResult expectedParseResult2 = new ParseResult("thiS", RETURN_VALUE);
        assertEquals(expectedParseResult1, parseResults.get(0));
        assertEquals(expectedParseResult2, parseResults.get(1));
    }

    @Test
    public void test_parseSingleOutput_should_return_first_match(){
        searchWords = new ArrayList<>(Arrays.asList(SEARCH_WORD_1));
        ParseInstructions parseInstructions = new ParseInstructions(6,8, " ",searchWords);
        ParseResult parseResult = OutputParser.parseSingleOutput(parseInstructions, commandOutput);
        ParseResult expectedParseResult = new ParseResult(SEARCH_WORD_1, RETURN_VALUE);
        assertEquals(expectedParseResult, parseResult);
    }

    @Test
    public void test_parseSingleOutput_should_return_invalid_parseResult(){
        searchWords = new ArrayList<>(Arrays.asList("No match"));
        ParseInstructions parseInstructions = new ParseInstructions(6,8, " ",searchWords);
        ParseResult parseResult = OutputParser.parseSingleOutput(parseInstructions, commandOutput);
        ParseResult expectedParseResult = new ParseResult("invalid", "invalid");
        assertEquals(expectedParseResult, parseResult);
    }

    @Test
    public void test_parseOutputWithSkips_should_return_two_matches() throws IOException{
        searchWords = new ArrayList<>(Arrays.asList(SEARCH_WORD_1));
        ParseInstructions parseInstructions = new ParseInstructions(1, 7, " ", searchWords);
        parseInstructions.setSkipWord("SkipFromKeyword");
        parseInstructions.setSkipUntilKeyword("skipUntilKeyword");
        ArrayList<String> commandSkipOutput = MockCommandExecutor.readFromFile(PARSE_OUTPUT_WITH_SKIPS_TEST_FILE);
        ArrayList<ParseResult> parseResults = OutputParser.parseOutPutWithSkips(parseInstructions, commandSkipOutput);
        ParseResult expectedParseResult = new ParseResult(SEARCH_WORD_1, RETURN_VALUE);
        assertEquals(expectedParseResult, parseResults.get(0));
        assertEquals(expectedParseResult, parseResults.get(1));
    }

    @Test
    public void test_skipToIndex_should_return_correct_index() throws IOException{
        searchWords = new ArrayList<>(Arrays.asList(SEARCH_WORD_1));
        ParseInstructions parseInstructions = new ParseInstructions(0, 0, " ", searchWords);
        parseInstructions.setSkipUntilKeyword("skipUntilKeyword");
        ArrayList<String> commandSkipOutput = MockCommandExecutor.readFromFile(PARSE_OUTPUT_WITH_SKIPS_TEST_FILE);
        int indexReturned = OutputParser.skipToIndex(3, parseInstructions, commandSkipOutput);
        int expectedReturnIndex = 10;
        assertEquals(expectedReturnIndex, indexReturned);
    }
}