package com.yahoo.yolean.concurrent;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Wraps a lazily initialised resource which needs to be shut down.
 *
 * @author jonmv
 */
public class Memoized<T, E extends Exception> implements Supplier<T>, AutoCloseable {

    /** Provides a tighter bound on the thrown exception type. */
    @FunctionalInterface
    public interface Closer<T, E extends Exception> { void close(T t) throws E; }

    private final Object monitor = new Object();
    private final Closer<T, E> closer;
    private volatile T wrapped;
    private Supplier<T> factory;

    public Memoized(Supplier<T> factory, Closer<T, E> closer) {
        this.factory = requireNonNull(factory);
        this.closer = requireNonNull(closer);
    }

    public static <T extends AutoCloseable> Memoized<T, ?> of(Supplier<T> factory) {
        return new Memoized<>(factory, AutoCloseable::close);
    }

    @Override
    public T get() {
        if (wrapped == null) synchronized (monitor) {
            if (factory != null) wrapped = factory.get();
            factory = null;
            if (wrapped == null) throw new IllegalStateException("already closed");
        }
        return wrapped;
    }

    @Override
    public void close() throws E {
        synchronized (monitor) {
            T maybe = wrapped;
            wrapped = null;
            factory = null;
            if (maybe != null) closer.close(maybe);
        }
    }

}