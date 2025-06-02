// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

/**
 * A pointer to a location in a YQL source program.
 */
final class Location {

    private final String programName;
    private final int lineNumber;
    private final int characterOffset;

    public Location(String programName, int lineNumber, int characterOffset) {
        this.programName = programName;
        this.lineNumber = lineNumber;
        this.characterOffset = characterOffset;
    }


    public int getLineNumber() {
        return lineNumber;
    }

    public int getCharacterOffset() {
        return characterOffset;
    }

    @Override
    public String toString() {
        if (programName != null) {
            return programName + ":L" + lineNumber + ":" + characterOffset;
        } else {
            return "L" + lineNumber + ":" + characterOffset;
        }
    }

}
