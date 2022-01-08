// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

import java.util.Optional;

/**
 * TODO: remove
 * @author hakonhall
 */
class Variable {
    private final String name;
    private final CursorRange firstVariableReferenceRange;
    private Optional<String> value = Optional.empty();

    Variable(String name, CursorRange firstVariableReferenceRange) {
        this.name = name;
        this.firstVariableReferenceRange = firstVariableReferenceRange;
    }

    void set(String value) { this.value = Optional.of(value); }
    Optional<String> get() { return value; }
}
