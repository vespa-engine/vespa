// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.collections.Pair;

import java.util.Objects;
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
            Pattern.compile("rankingExpression\\(([a-zA-Z0-9_.]+)(@[a-f0-9]+[.a-f0-9]*)?\\)(\\.rankingScript)?");
    private static final Pattern externalReferencePattern =
            Pattern.compile("rankingExpression\\(([a-zA-Z0-9_.]+)(@[a-f0-9]+[.a-f0-9]*)?\\)(\\.expressionName)?");
    private static final Pattern argumentTypePattern =
            Pattern.compile("rankingExpression\\(([a-zA-Z0-9_.]+)(@[a-f0-9]+[.a-f0-9]*)?\\)\\.([a-zA-Z0-9_]+)\\.type");
    private static final Pattern returnTypePattern =
            Pattern.compile("rankingExpression\\(([a-zA-Z0-9_.]+)(@[a-f0-9]+[.a-f0-9]*)?\\)\\.type");

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
    public String toString() { return "reference to function '" + name + "'" +
                                      ( instance != null ? " instance '" + instance + "'" : ""); }

    @Override
    public int hashCode() { return Objects.hash(name, instance); }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof FunctionReference)) return false;
        FunctionReference other = (FunctionReference)o;
        if ( ! Objects.equals(this.name, other.name)) return false;
        if ( ! Objects.equals(this.instance, other.instance)) return false;
        return true;
    }

    /** Returns a function reference from the given serial form, or empty if the string is not a valid reference */
    static Optional<FunctionReference> fromSerial(String serialForm) {
        Matcher expressionMatcher = referencePattern.matcher(serialForm);
        if ( ! expressionMatcher.matches()) return Optional.empty();

        String name = expressionMatcher.group(1);
        String instance = expressionMatcher.group(2);
        return Optional.of(new FunctionReference(name, instance));
    }

    /** Returns a function reference from the given serial form, or empty if the string is not a valid reference */
    static Optional<FunctionReference> fromExternalSerial(String serialForm) {
        Matcher expressionMatcher = externalReferencePattern.matcher(serialForm);
        if ( ! expressionMatcher.matches()) return Optional.empty();

        String name = expressionMatcher.group(1);
        String instance = expressionMatcher.group(2);
        return Optional.of(new FunctionReference(name, instance));
    }

    /**
     * Returns a function reference and argument name string from the given serial form,
     * or empty if the string is not a valid function argument serial form
     */
    static Optional<Pair<FunctionReference, String>> fromTypeArgumentSerial(String serialForm) {
        Matcher expressionMatcher = argumentTypePattern.matcher(serialForm);
        if ( ! expressionMatcher.matches()) return Optional.empty();

        String name = expressionMatcher.group(1);
        String instance = expressionMatcher.group(2);
        String argument = expressionMatcher.group(3);
        return Optional.of(new Pair<>(new FunctionReference(name, instance), argument));
    }

    /**
     * Returns a function reference from the given return type serial form,
     * or empty if the string is not a valid function return typoe serial form
     */
    static Optional<FunctionReference> fromReturnTypeSerial(String serialForm) {
        Matcher expressionMatcher = returnTypePattern.matcher(serialForm);
        if ( ! expressionMatcher.matches()) return Optional.empty();

        String name = expressionMatcher.group(1);
        String instance = expressionMatcher.group(2);
        return Optional.of(new FunctionReference(name, instance));
    }

    public static FunctionReference fromName(String name) {
        return new FunctionReference(name, null);
    }

}
