// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.commons.parser;

import java.util.List;

/**
 * Contains instructions of how a command line output should be parsed
 *
 * @author sgrostad
 * @author olaaaun
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
