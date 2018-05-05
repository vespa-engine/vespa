// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.editor;

import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

import java.nio.file.Path;

/**
 * @author hakon
 */
public class FileEditor {
    private final UnixPath path;
    private final StringEditor stringEditor;

    private String fileText;
    private Version fileVersion;

    public static FileEditor open(Path path) {
        UnixPath unixPath = new UnixPath(path);
        String text = unixPath.readUtf8File();
        StringEditor stringEditor = new StringEditor(text);
        return new FileEditor(unixPath, text, stringEditor);
    }

    private FileEditor(UnixPath path, String fileText, StringEditor stringEditor) {
        this.path = path;
        this.fileText = fileText;
        this.stringEditor = stringEditor;
        fileVersion = stringEditor.bufferVersion();
    }

    public Cursor cursor() {
        return stringEditor.cursor();
    }

    public void reloadFile() {
        fileText = path.readUtf8File();
        stringEditor.cursor().deleteAll().write(fileText);
        fileVersion = stringEditor.bufferVersion();
    }

    public boolean save() {
        Version bufferVersion = stringEditor.bufferVersion();
        if (bufferVersion.equals(fileVersion)) {
            return false;
        }

        String newText = stringEditor.cursor().getBufferText();
        if (newText.equals(fileText)) {
            return false;
        }

        path.writeUtf8File(newText);
        fileVersion = bufferVersion;
        return true;
    }
}
