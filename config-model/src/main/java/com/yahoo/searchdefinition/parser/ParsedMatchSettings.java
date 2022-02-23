package com.yahoo.searchdefinition.parser;

/**
 * This class holds the extracted information after parsing a "match"
 * block, using simple data structures as far as possible.  Do not put
 * advanced logic here!
 * @author arnej27959
 **/
public class ParsedMatchSettings {

    void setType(MatchType type) {}
    void setCase(MatchCase type) {}
    void setAlgorithm(MatchAlgorithm type) {}
    void setExactTerminator(String terminator) {}
    void setGramSize(int size) {}
    void setMaxLength(int size) {}
}
