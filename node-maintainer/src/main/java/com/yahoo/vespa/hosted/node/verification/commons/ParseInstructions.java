package com.yahoo.vespa.hosted.node.verification.commons;

import java.util.ArrayList;

/**
 * Created by sgrostad on 17/07/2017.
 */
public class ParseInstructions {

    private final int searchElementIndex;
    private final int valueElementIndex;
    private final String splitRegex;
    private final ArrayList<String> searchWords;
    private String skipWord;
    private String skipUntilKeyword;

    public ParseInstructions(int searchElementIndex, int returnElementNum, String splitRegex, ArrayList<String> searchWords){
        this.searchElementIndex = searchElementIndex;
        this.valueElementIndex = returnElementNum;
        this.splitRegex = splitRegex;
        this.searchWords = searchWords;
    }

    public int getSearchElementIndex() {return searchElementIndex;}

    public int getValueElementIndex() {return valueElementIndex;}

    public String getSplitRegex() {return splitRegex;}

    public ArrayList<String> getSearchWords() {return searchWords;}

    public void setSkipWord(String skipWord){
        this.skipWord = skipWord;
    }

    public void setSkipUntilKeyword(String skipUntilWord) {
        this.skipUntilKeyword = skipUntilWord;
    }

    public String getSkipWord(){
        return this.skipWord;
    }

    public String getSkipUntilKeyword(){
        return this.skipUntilKeyword;
    }
}
