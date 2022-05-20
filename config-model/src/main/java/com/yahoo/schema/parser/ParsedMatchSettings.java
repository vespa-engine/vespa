package com.yahoo.schema.parser;

import com.yahoo.schema.document.Case;
import com.yahoo.schema.document.MatchType;
import com.yahoo.schema.document.MatchAlgorithm;

import java.util.Optional;

/**
 * This class holds the extracted information after parsing a "match"
 * block, using simple data structures as far as possible.  Do not put
 * advanced logic here!
 * @author arnej27959
 **/
public class ParsedMatchSettings {

    private MatchType matchType = null;
    private Case matchCase = null;
    private MatchAlgorithm matchAlgorithm = null;
    private String exactTerminator = null;
    private Integer gramSize = null;
    private Integer maxLength = null;

    Optional<MatchType> getMatchType() { return Optional.ofNullable(matchType); }
    Optional<Case> getMatchCase() { return Optional.ofNullable(matchCase); }
    Optional<MatchAlgorithm> getMatchAlgorithm() { return Optional.ofNullable(matchAlgorithm); }
    Optional<String> getExactTerminator() { return Optional.ofNullable(exactTerminator); }
    Optional<Integer> getGramSize() { return Optional.ofNullable(gramSize); }
    Optional<Integer> getMaxLength() { return Optional.ofNullable(maxLength); }

    // TODO - consider allowing each set only once:
    void setType(MatchType value) { this.matchType = value; }
    void setCase(Case value) { this.matchCase = value; }
    void setAlgorithm(MatchAlgorithm value) { this.matchAlgorithm = value; }
    void setExactTerminator(String value) { this.exactTerminator = value; }
    void setGramSize(int value) { this.gramSize = value; }
    void setMaxLength(int value) { this.maxLength = value; }
}
