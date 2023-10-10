// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield;

public class AnnotateStringFieldPart implements FieldPart {

    public static final char RAW_ANNOTATE_BEGIN_CHAR = '\uFFF9';
    public static final char RAW_ANNOTATE_SEPARATOR_CHAR = '\uFFFA';
    public static final char RAW_ANNOTATE_END_CHAR = '\uFFFB';

    private String content;
    private String rawContent;

    public AnnotateStringFieldPart(String source, int index) {
        content = "";
        rawContent = "";
        if (source.charAt(index) == RAW_ANNOTATE_BEGIN_CHAR) {
            int sep = source.indexOf(RAW_ANNOTATE_SEPARATOR_CHAR, index);
            int end = source.indexOf(RAW_ANNOTATE_END_CHAR, index);

            if (sep != -1) {
                rawContent = source.substring(index + 1, sep);
                if (end != -1 && end > sep) {
                    content = source.substring(sep + 1, end);
                }
                else {
                    content = rawContent;
                }
            }
        }
    }

    public boolean isFinal() { return false; }

    public boolean isToken() { return true; }

    public String getContent() { return rawContent; }

    public void setContent(String content) {
        this.content = content;
    }

    public String toString() { return content; }

}
