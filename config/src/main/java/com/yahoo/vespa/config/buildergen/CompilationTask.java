// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.buildergen;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;

/**
 * Represents a compilation task that can be run and also collects diagnostic messages from the compilation.
 * TODO: Assumes that diagnostics is the same as given to the task, not ideal.
 *
 * @author lulf
 * @since 5.2
 */
class CompilationTask {
    private final JavaCompiler.CompilationTask task;
    private final DiagnosticCollector<JavaFileObject> diagnostics;

    CompilationTask(JavaCompiler.CompilationTask task, DiagnosticCollector<JavaFileObject> diagnostics) {
        this.task = task;
        this.diagnostics = diagnostics;
    }

    void call() {
        boolean success = task.call();
        if (!success) {
            throw new IllegalArgumentException("Compilation diagnostics: " + getDiagnosticMessage());
        }
    }

    private String getDiagnosticMessage() {
        StringBuilder diagnosticMessages = new StringBuilder();
        for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
            diagnosticMessages.append(diagnostic.getCode()).append("\n");
            diagnosticMessages.append(diagnostic.getKind()).append("\n");
            diagnosticMessages.append(diagnostic.getPosition()).append("\n");
            diagnosticMessages.append(diagnostic.getStartPosition()).append("\n");
            diagnosticMessages.append(diagnostic.getEndPosition()).append("\n");
            diagnosticMessages.append(diagnostic.getSource()).append("\n");
            diagnosticMessages.append(diagnostic.getMessage(null)).append("\n");
        }
        return diagnosticMessages.toString();
    }
}
