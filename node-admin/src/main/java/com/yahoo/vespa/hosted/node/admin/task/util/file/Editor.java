// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.IOExceptionUtil.uncheck;

/**
 * An editor meant to edit small line-based files like /etc/fstab.
 *
 * @author hakonhall
 */
public class Editor {
    private static final Logger logger = Logger.getLogger(Editor.class.getName());
    private static final Charset ENCODING = StandardCharsets.UTF_8;

    private static int maxLength = 300;

    private final Path path;
    private final LineEditor editor;

    public Editor(Path path, LineEditor editor) {
        this.path = path;
        this.editor = editor;
    }

    /**
     * Read the file which must be encoded in UTF-8, use the LineEditor to edit it,
     * and any modifications were done write it back and return true.
     */
    public boolean converge(TaskContext context) {
        List<String> lines = uncheck(() -> Files.readAllLines(path, ENCODING));
        List<String> newLines = new ArrayList<>();
        StringBuilder diff = new StringBuilder();
        boolean modified = false;

        for (String line : lines) {
            LineEdit edit = editor.edit(line);
            if (!edit.prependLines().isEmpty()) {
                modified = true;
                maybeAdd(diff, edit.prependLines());
                newLines.addAll(edit.prependLines());
            }

            switch (edit.getType()) {
                case REPLACE:
                    modified = true;
                    maybeRemove(diff, line);
                    break;
                case NONE:
                    newLines.add(line);
                    break;
                default: throw new IllegalArgumentException("Unknown EditType " + edit.getType());
            }

            if (!edit.appendLines().isEmpty()) {
                modified = true;
                maybeAdd(diff, edit.appendLines());
                newLines.addAll(edit.appendLines());
            }
        }

        List<String> linesToAppend = editor.onComplete();
        if (!linesToAppend.isEmpty()) {
            modified = true;
            newLines.addAll(linesToAppend);
            maybeAdd(diff, linesToAppend);
        }

        if (!modified) {
            return false;
        }

        String diffDescription = diffTooLarge(diff) ? "" : ":\n" + diff.toString();
        context.recordSystemModification(logger, "Patching file " + path + diffDescription);
        uncheck(() -> Files.write(path, newLines, ENCODING));
        return true;
    }

    private static void maybeAdd(StringBuilder diff, List<String> lines) {
        for (String line : lines) {
            if (!diffTooLarge(diff)) {
                diff.append('+').append(line).append('\n');
            }
        }
    }

    private static void maybeRemove(StringBuilder diff, String line) {
        if (!diffTooLarge(diff)) {
            diff.append('-').append(line).append('\n');
        }
    }

    private static boolean diffTooLarge(StringBuilder diff) {
        return diff.length() > maxLength;
    }
}
