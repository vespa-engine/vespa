// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.export;

import java.util.List;

/**
 * Exception that contains outcome of selection and options.
 *
 * @author johsol
 */
final class SelectionException extends RuntimeException {
    private final PathSelector.Outcome outcome;
    private final String kind;
    private final List<PathSelector.Row> options;

    SelectionException(PathSelector.Outcome outcome, String kind, String message, List<PathSelector.Row> options) {
        super(message);
        this.outcome = outcome;
        this.kind = kind;
        this.options = options;
    }

    PathSelector.Outcome outcome() { return outcome; }
    String kind() { return kind; }
    List<PathSelector.Row> options() { return options; }
}
