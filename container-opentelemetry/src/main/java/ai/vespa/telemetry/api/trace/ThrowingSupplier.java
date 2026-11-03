// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.telemetry.api.trace;

/**
 * A {@link java.util.function.Supplier} that may throw a checked exception.
 *
 * <p>Instrumenting a method which declares {@code throws} is impossible with {@code Supplier}, whose
 * {@code get()} cannot carry one — {@code SearchInvoker.search} declares {@code throws IOException}, for
 * instance. This interface exists so a span can wrap such a body without the instrumentation changing the
 * exception behaviour of the code it measures.</p>
 *
 * <p>The value-returning {@code instrument} methods take this INSTEAD OF {@code Supplier}, not in addition to it: with both
 * present javac reports "reference to inSpan is ambiguous", for non-throwing and throwing lambdas alike.
 * Replacing costs callers nothing — a body that throws no checked exception infers {@code E =
 * RuntimeException}, so an ordinary call site needs neither a {@code throws} clause nor a {@code catch}.</p>
 *
 * @author onur
 */
@FunctionalInterface
public interface ThrowingSupplier<T, E extends Exception> {

    T get() throws E;

}
