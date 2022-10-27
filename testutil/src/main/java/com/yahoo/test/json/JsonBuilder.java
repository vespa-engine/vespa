// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test.json;

import com.fasterxml.jackson.core.io.JsonStringEncoder;

/**
 * String buffer for building a formatted JSON.
 *
 * @author hakonhall
 */
class JsonBuilder {
    private final JsonStringEncoder jsonStringEncoder = JsonStringEncoder.getInstance();
    private final StringBuilder builder = new StringBuilder();
    private final String indentation;
    private final boolean multiLine;
    private final String colon;

    private boolean bol = true;
    private int level = 0;

    static JsonBuilder forCompactJson() { return new JsonBuilder(0, true); }
    static JsonBuilder forMultiLineJson(int spacesPerIndent) { return new JsonBuilder(spacesPerIndent, false); }

    JsonBuilder(int spacesPerIndent, boolean compact) {
        this.indentation = compact ? "" : " ".repeat(spacesPerIndent);
        this.multiLine = !compact;
        this.colon = compact ? ":" : ": ";
    }

    void appendLineAndIndent(String text) { appendLineAndIndent(text, 0); }

    void newLineIndentAndAppend(int levelShift, String text) {
        appendNewLine();
        indent(levelShift);
        append(text);
    }

    void appendLineAndIndent(String text, int levelShift) {
        appendLine(text);
        indent(levelShift);
        append("");
    }

    void appendColon() { builder.append(colon); }

    void appendStringValue(String rawString) {
        builder.append('"');
        jsonStringEncoder.quoteAsString(rawString, builder);
        builder.append('"');
    }

    void append(String textWithoutNewline) {
        if (bol) {
            builder.append(indentation.repeat(level));
            bol = false;
        }

        builder.append(textWithoutNewline);
    }

    private void indent(int levelShift) { level += levelShift; }

    private void appendLine(String text) {
        append(text);
        appendNewLine();
    }

    private void appendNewLine() {
        if (multiLine) builder.append('\n');
        bol = true;
    }

    @Override
    public String toString() { return builder.toString(); }
}
