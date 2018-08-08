// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A reference to a function.
 * The function may be
 * - free: Callable from users of models, or
 * - bound: Representing a specific invocation from another ranking expression.
 * In bound functions, any arguments are replaced by the values supplied in the function invocation.
 * Function references has a serial form (textual representation) used in ranking expressions received in ranking
 * expression configurations.
 *
 * This is immutable.
 *
 * @author bratseth
 */
class FunctionReference {

    private static final Pattern referencePattern =
            Pattern.compile("rankingExpression\\(([a-zA-Z0-9_]+)(@[a-f0-9]+\\.[a-f0-9]+)?\\)(\\.rankingScript)?");

    /** The name of the function referenced */
    private final String name;

    /** The id of the specific invocation of the function, or null if it is free */
    private final String instance;

    private FunctionReference(String name, String instance) {
        this.name = name;
        this.instance = instance;
    }

    /** Returns the name of the function referenced */
    String functionName() { return name; }

    boolean isFree() {
        return instance == null;
    }

    String serialForm() {
        return "rankingExpression(" + name + (instance != null ? instance : "") + ")";
    }

    @Override
    public String toString() { return serialForm(); }

    // TODO: Equals and hashcode

    /** Returns a function reference from the given serial form, or empty if the string is not a valid reference */
    static Optional<FunctionReference> fromSerial(String serialForm) {
        Matcher expressionMatcher = referencePattern.matcher(serialForm);
        if ( ! expressionMatcher.matches()) return Optional.empty();

        String name = expressionMatcher.group(1);
        String instance = expressionMatcher.group(2);
        return Optional.of(new FunctionReference(name, instance));
    }

}
