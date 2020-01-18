// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.yahoo.vespa.hosted.node.admin.task.util.file.LineEdit;
import com.yahoo.vespa.hosted.node.admin.task.util.file.LineEditor;

import java.util.List;

/**
 * An editor that only cares about the REDIRECT statement
 *
 * @author smorgrav
 */
class NatTableLineEditor implements LineEditor {

    private final String redirectRule;
    private boolean redirectExists;

    private NatTableLineEditor(String redirectRule) {
        this.redirectRule = redirectRule;
    }

    static NatTableLineEditor from(String redirectRule) {
        return new NatTableLineEditor(redirectRule);
    }

    @Override
    public LineEdit edit(String line) {
        if (line.endsWith("REDIRECT")) {
            if (redirectExists) {
                // Only allow one redirect rule
                return LineEdit.remove();
            } else {
                redirectExists = true;
                if (line.equals(redirectRule)) {
                    return LineEdit.none();
                } else {
                    return LineEdit.replaceWith(redirectRule);
                }
            }
        } else return LineEdit.none();
    }

    @Override
    public List<String> onComplete() {
        if (redirectExists) return List.of();
        return List.of(redirectRule);
    }
}
