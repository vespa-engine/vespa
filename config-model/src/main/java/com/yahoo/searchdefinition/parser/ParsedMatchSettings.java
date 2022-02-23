package com.yahoo.searchdefinition.parser;

import java.util.Optional;

/**
 * This class holds the extracted information after parsing a "match"
 * block, using simple data structures as far as possible.  Do not put
 * advanced logic here!
 * @author arnej27959
 **/
public class ParsedMatchSettings {

    private Optional<MatchType> matchType = Optional.empty();
    private Optional<MatchCase> matchCase = Optional.empty();
    private Optional<MatchAlgorithm> matchAlgorithm = Optional.empty();
    private Optional<String> exactTerminator = Optional.empty();
    private Optional<Integer> gramSize = Optional.empty();
    private Optional<Integer> maxLength = Optional.empty();

    Optional<MatchType> getMatchType() { return this.matchType; }
    Optional<MatchCase> getMatchCase() { return this.matchCase; }
    Optional<MatchAlgorithm> getMatchAlgorithm() { return this.matchAlgorithm; }
    Optional<String> getExactTerminator() { return this.exactTerminator; }
    Optional<Integer> getGramSize() { return this.gramSize; }
    Optional<Integer> getMaxLength() { return this.maxLength; }

    // TODO - consider allowing each set only once:
    void setType(MatchType value) { this.matchType = Optional.of(value); }
    void setCase(MatchCase value) { this.matchCase = Optional.of(value); }
    void setAlgorithm(MatchAlgorithm value) { this.matchAlgorithm = Optional.of(value); }
    void setExactTerminator(String value) { this.exactTerminator = Optional.of(value); }
    void setGramSize(int value) { this.gramSize = Optional.of(value); }
    void setMaxLength(int value) { this.maxLength = Optional.of(value); }
}
