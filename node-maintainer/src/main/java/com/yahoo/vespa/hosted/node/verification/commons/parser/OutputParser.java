package com.yahoo.vespa.hosted.node.verification.commons.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses terminal command output, and returns results based on ParseInstructions
 *
 * @author sgrostad
 */
public class OutputParser {

    public static List<ParseResult> parseOutput(ParseInstructions parseInstructions, List<String> commandOutput) {
        List<ParseResult> results = new ArrayList<>();
        int searchElementIndex = parseInstructions.getSearchElementIndex();
        int valueElementIndex = parseInstructions.getValueElementIndex();
        List<String> searchWords = parseInstructions.getSearchWords();
        for (String line : commandOutput) {
            String[] lineSplit = line.trim().split(parseInstructions.getSplitRegex());
            if (lineSplit.length <= Math.max(searchElementIndex, valueElementIndex)) {
                continue;
            }
            String searchWordCandidate = lineSplit[searchElementIndex].trim();
            boolean searchWordCandidateMatch = matchingSearchWord(searchWords, searchWordCandidate);
            if (searchWordCandidateMatch) {
                String value = lineSplit[valueElementIndex];
                results.add(new ParseResult(searchWordCandidate, value.trim()));
            }
        }
        return results;
    }

    public static ParseResult parseSingleOutput(ParseInstructions parseInstructions, List<String> commandOutput) {
        List<ParseResult> parseResults = parseOutput(parseInstructions, commandOutput);
        if (parseResults.size() == 0) {
            return new ParseResult("invalid", "invalid");
        }
        return parseResults.get(0);
    }

    private static boolean matchingSearchWord(List<String> searchWords, String searchWordCandidate) {
        return searchWords.stream().anyMatch(w -> Pattern.compile(w).matcher(searchWordCandidate).matches());
    }

}
