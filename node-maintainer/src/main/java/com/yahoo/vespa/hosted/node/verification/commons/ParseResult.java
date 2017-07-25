package com.yahoo.vespa.hosted.node.verification.commons;

/**
 * Created by sgrostad on 17/07/2017.
 */
public class ParseResult {
    private final String searchWord;
    private final String value;

    public ParseResult(String searchWord, String value) {
        this.searchWord = searchWord;
        this.value = value;
    }

    public String getSearchWord() {
        return searchWord;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object obj){
        if (this == obj) return true;
        if (obj instanceof ParseResult) {
            ParseResult parseResult = (ParseResult) obj;
            if (this.searchWord.equals(parseResult.getSearchWord()) && this.value.equals(parseResult.getValue())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode(){
        int hash = 17;
        hash = 37 * hash + searchWord.length() +  value.length();
        return hash;
    }

    @Override
    public String toString(){
        return "Search word: " + searchWord + ", Value: " + value;
    }
}
