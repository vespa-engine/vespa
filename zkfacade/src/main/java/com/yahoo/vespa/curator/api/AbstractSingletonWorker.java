package com.yahoo.vespa.curator.api;

import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.vespa.curator.api.VespaCurator.SingletonWorker;
import com.yahoo.yolean.UncheckedInterruptedException;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Super-class for implementations of {@link SingletonWorker}.
 * Users should call {@link VespaCurator#registerSingleton} at construction, and
 * {@link VespaCurator#unregisterSingleton} at deconstruction.
 * If this fails activation on registration, it will immediately unregister as well, before propagating the error.
 * Consequently, registering this on construction will allow detecting a failed component generation, and instead
 * retain the previous generation, provided enough verification is done in {@link #activate()}.
 * The default ID to use with registration is the concrete class name, e.g., {@code my.example.Singleton}.
 *
 * @author jonmv
 */
public abstract class AbstractSingletonWorker extends AbstractComponent implements SingletonWorker {

    private final AtomicReference<VespaCurator> owner = new AtomicReference<>();

    /**
     * The singleton ID to use when registering this with a {@link VespaCurator}.
     * At most one singleton worker with the given ID will be active, in the cluster, at any time.
     * {@link VespaCurator#isActive(String)} may be polled to see whether this container is currently
     * allowed to have an active singleton with the given ID.
     */
    protected String id() { return getClass().getName(); }

    /**
     * Call this at the end of construction of the child or owner.
     * If this activates the singleton, this happens synchronously, and any errors are propagated here.
     * If this replaces an already active singleton, its deactivation is also called, prior to activation of this.
     * If (de)activation is not complete within the given timeout, a timeout exception is thrown.
     * If an error occurs (due to failed activation), unregistration is automatically attempted, with the same timeout.
     */
    protected final void register(VespaCurator curator, Duration timeout) {
        if ( ! owner.compareAndSet(null, curator)) {
            throw new IllegalArgumentException(this + " is already registered with " + owner.get());
        }
        try {
            await(curator.registerSingleton(id(), this), timeout, "register");
        }
        catch (RuntimeException e) {
            try {
                unregister(timeout);
            }
            catch (Exception f) {
                e.addSuppressed(f);
            }
            throw e;
        }
    }

    /**
     * Call this at the start of deconstruction of the child!
     * If this singleton is active, deactivation will be called synchronously, and errors propagated here.
     * If this also triggers activation of a new singleton, its activation is called after deactivation of this.
     * If (de)activation is not complete within the given timeout, a timeout exception is thrown.
     */
    protected final void unregister(Duration timeout) {
        VespaCurator curator = owner.getAndSet(null);
        if (curator == null) {
            throw new IllegalArgumentException(this + " was not registered with any owners");
        }
        await(curator.unregisterSingleton(this), timeout, "unregister");
    }

    private void await(Future<?> future, Duration timeout, String verb) {
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
            future.cancel(true);
            throw new UncheckedInterruptedException("interrupted while " + verb + "ing " + this, e, true);
        }
        catch (TimeoutException e) {
            future.cancel(true);
            throw new UncheckedTimeoutException("timed out while " + verb + "ing " + this, e);
        }
        catch (ExecutionException e) {
            throw new RuntimeException("failed to " + verb + " " + this, e.getCause());
        }
    }

}
