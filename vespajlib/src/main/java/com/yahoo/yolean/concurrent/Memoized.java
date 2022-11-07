package com.yahoo.yolean.concurrent;

import com.yahoo.api.annotations.Beta;

import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Wraps a lazily initialised resource which needs to be shut down.
 * The wrapped supplier may not return {@code null}, and should be retryable on failure.
 * If it throws, it will be retried if {@link #get} is retried. A supplier that fails to
 * clean up partial state on failure may cause a resource leak.
 *
 * @author jonmv
 */
@Beta
public class Memoized<T, E extends Exception> implements Supplier<T>, AutoCloseable {

    /**
     * Provides a tighter bound on the thrown exception type.
     */
    @FunctionalInterface
    public interface Closer<T, E extends Exception> {

        void close(T t) throws E;

    }


    private final Object monitor = new Object();
    private final Closer<T, E> closer;
    private volatile T wrapped;
    private Supplier<T> factory;

    /** Returns a new Memoized which has no close method. */
    public Memoized(Supplier<T> factory) {
        this(factory, __ -> { });
    }

    /** Returns a new Memoized with the given factory and closer. */
    public Memoized(Supplier<T> factory, Closer<T, E> closer) {
        this.factory = requireNonNull(factory);
        this.closer = requireNonNull(closer);
    }

    /** Returns a generic AutoCloseable Memoized with the given AutoCloseable-supplier. */
    public static <T extends AutoCloseable> Memoized<T, ?> of(Supplier<T> factory) {
        return new Memoized<>(factory, AutoCloseable::close);
    }

    /** Composes the given memoized with a function taking its output as an argument to produce a new Memoized, with the given closer. */
    public static <T, U, E extends Exception> Memoized<U, E> combine(Memoized<T, ? extends E> inner, Function<T, U> outer, Closer<U, ? extends E> closer) {
        return new Memoized<>(() -> outer.apply(inner.get()), compose(closer, inner::close));
    }

    @Override
    public T get() {
        // Double-checked locking: try the variable, and if not initialized, try to initialize it.
        if (wrapped == null) synchronized (monitor) {
            // Ensure the factory is called only once, by clearing it once successfully called.
            if (factory != null) wrapped = requireNonNull(factory.get());
            factory = null;

            // If we found the factory, we won the initialization race, and return normally; otherwise
            // if wrapped is non-null, we lost the race, wrapped was set by the winner, and we return; otherwise
            // we tried to initialise because wrapped was cleared by closing this, and we fail.
            if (wrapped == null) throw new IllegalStateException("already closed");
        }
        return wrapped;
    }

    @Override
    public void close() throws E {
        // Alter state only when synchronized with calls to get().
        synchronized (monitor) {
            // Ensure we only try to close the generated resource once, by clearing it after picking it up here.
            T maybe = wrapped;
            wrapped = null;
            // Clear the factory, to signal this has been closed.
            factory = null;
            if (maybe != null) closer.close(maybe);
        }
    }

    private interface Thrower<E extends Exception> { void call() throws E; }

    private static <T, E extends Exception> Closer<T, E> compose(Closer<T, ? extends E> outer, Thrower<? extends E> inner) {
        return parent -> {
            Exception thrown = null;
            try {
                outer.close(parent);
            }
            catch (Exception e) {
                thrown = e;
            }
            try {
                inner.call();
            }
            catch (Exception e) {
                if (thrown != null) thrown.addSuppressed(e);
                else thrown = e;
            }
            @SuppressWarnings("unchecked")
            E e = (E) thrown;
            if (e != null) throw e;
        };
    }

}