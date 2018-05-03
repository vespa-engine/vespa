// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.editor;

import java.util.regex.Matcher;

/**
 * Represents a pattern match of a line
 *
 * @author hakon
 */
public class Match {
    private final int lineIndex;
    private final String line;
    private final Matcher matcher;

    Match(int lineIndex, String line, Matcher matcher) {
        this.lineIndex = lineIndex;
        this.line = line;
        this.matcher = matcher;
    }

    /** The part of the line before the match */
    public String prefix() {
        return line.substring(0, matcher.start());
    }

    /** The part of the line that matched */
    public String match() {
        return matcher.group();
    }

    /** The part of the line that followed the match */
    public String suffix() {
        return line.substring(matcher.end());
    }

    public Position startOfMatch() {
        return new Position(lineIndex, matcher.start());
    }

    public Position endOfMatch() {
        return new Position(lineIndex, matcher.end());
    }

    public int groupCount() {
        return matcher.groupCount();
    }

    public String group(int groupnr) {
        return matcher.group(groupnr);
    }
}
