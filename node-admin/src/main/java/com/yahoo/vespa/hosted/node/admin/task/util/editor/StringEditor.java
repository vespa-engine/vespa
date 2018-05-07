// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.editor;

/**
 * Edits multi-line text.
 *
 * @author hakon
 */
public class StringEditor {
    private final TextBuffer textBuffer;
    private final Cursor cursor;

    public StringEditor() {
        this("");
    }

    public StringEditor(String text) {
        textBuffer = new TextBufferImpl(text);
        cursor = new CursorImpl(textBuffer);
    }

    public Cursor cursor() {
        return cursor;
    }

    public Version bufferVersion() {
        return textBuffer.getVersion();
    }
}
