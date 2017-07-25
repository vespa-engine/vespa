package com.yahoo.vespa.hosted.node.verification.commons;

import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Created by sgrostad on 17/07/2017.
 * Parses terminal command output, and returns results based on ParseInstructions
 */
public class OutputParser {

    public static ArrayList<ParseResult> parseOutput(ParseInstructions parseInstructions, ArrayList<String> commandOutput) {
        ArrayList<ParseResult> results = new ArrayList<>();
        int searchElementIndex = parseInstructions.getSearchElementIndex();
        int valueElementIndex = parseInstructions.getValueElementIndex();
        ArrayList<String> searchWords = parseInstructions.getSearchWords();
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

    public static ParseResult parseSingleOutput(ParseInstructions parseInstructions, ArrayList<String> commandOutput) {
        ArrayList<ParseResult> parseResults = parseOutput(parseInstructions, commandOutput);
        if (parseResults.size() == 0) {
            return new ParseResult("invalid", "invalid");
        }
        return parseResults.get(0);
    }

    public static ArrayList<ParseResult> parseOutPutWithSkips(ParseInstructions parseInstructions, ArrayList<String> commandOutput) {
        ArrayList<ParseResult> results = new ArrayList<>();
        int searchElementIndex = parseInstructions.getSearchElementIndex();
        int valueElementIndex = parseInstructions.getValueElementIndex();
        String skipWord = parseInstructions.getSkipWord();
        ArrayList<String> searchWords = parseInstructions.getSearchWords();
        for (int i = 0; i < commandOutput.size(); i++) {
            String line = commandOutput.get(i);
            String[] lineSplit = line.trim().split(parseInstructions.getSplitRegex());
            if (lineSplit.length <= Math.max(searchElementIndex, valueElementIndex)) {
                continue;
            }
            if (lineSplit[searchElementIndex].equals(skipWord)) {
                i = skipToIndex(i, parseInstructions, commandOutput);
                continue;
            }
            String searchWordCandidate = lineSplit[searchElementIndex];
            boolean searchWordCandidateMatch = matchingSearchWord(searchWords, searchWordCandidate);
            if (searchWordCandidateMatch) {
                String value = lineSplit[valueElementIndex];
                results.add(new ParseResult(searchWordCandidate, value.trim()));
            }
        }
        return results;
    }

    protected static int skipToIndex(int index, ParseInstructions parseInstructions, ArrayList<String> commandOutput) {
        String skipUntilKeyword = parseInstructions.getSkipUntilKeyword();
        int returnIndex = commandOutput.size();
        for (int i = index; i < commandOutput.size(); i++) {
            String line = commandOutput.get(i);
            if (line.equals(skipUntilKeyword)) {
                returnIndex = i;
                break;
            }
        }
        return returnIndex - 1;
    }

    private static boolean matchingSearchWord(ArrayList<String> searchWords, String searchWordCandidate) {
        return searchWords.stream().anyMatch(w -> Pattern.compile(w).matcher(searchWordCandidate).matches());
    }

}
