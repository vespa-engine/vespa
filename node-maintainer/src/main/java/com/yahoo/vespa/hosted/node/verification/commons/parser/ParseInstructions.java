package com.yahoo.vespa.hosted.node.verification.commons.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * @author sgrostad
 */
public class ParseInstructions {

    private final int searchElementIndex;
    private final int valueElementIndex;
    private final String splitRegex;
    private final List<String> searchWords;

    public ParseInstructions(int searchElementIndex, int returnElementNum, String splitRegex, List<String> searchWords) {
        this.searchElementIndex = searchElementIndex;
        this.valueElementIndex = returnElementNum;
        this.splitRegex = splitRegex;
        this.searchWords = searchWords;
    }

    public int getSearchElementIndex() {
        return searchElementIndex;
    }

    public int getValueElementIndex() {
        return valueElementIndex;
    }

    public String getSplitRegex() {
        return splitRegex;
    }

    public List<String> getSearchWords() {
        return searchWords;
    }

}
