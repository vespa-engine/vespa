// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.api;

import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.path.Path;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * A client for a ZooKeeper cluster running inside Vespa. Applications that want to use ZooKeeper can inject this in
 * their code.
 *
 * @author mpolden
 * @author jonmv
 */
public interface VespaCurator {

    /** Returns the stat for the node at the given path, or empty if no node exists at that path. */
    Optional<Meta> stat(Path path);

    /** Returns the content and stat for the node at the given path, or empty if no node exists at that path. */
    Optional<Data> read(Path path);

    /**
     * Writes the given data to a node at the given path, creating it and its parents as needed, and returns the
     * stat of the modified node. Failure to write, due to connection loss, is retried a limited number of times.
     */
    Meta write(Path path, byte[] data);

    /**
     * Atomically compares the version in the stat of the node at the given path, with the expected version, and then:
     * if they are equal, attempts the write operation (see {@link #write(Path, byte[])});
     * otherwise, return empty.
     */
    Optional<Meta> write(Path path, byte[] data, int expectedVersion);

    /** Recursively deletes any node at the given path, and any children it may have. */
    void deleteAll(Path path);

    /** Deletes the node at the given path. Failres due to connection loss are retried a limited number of times. */
    void delete(Path path);

    /**
     * Atomically compares the version in the stat of the node at the given path, with the expected version, and then:
     * if they are equal, attempts the delete operation (see {@link #delete(Path)}), and returns {@code} true;
     * otherwise, returns {@code false}.
     */
    boolean delete(Path path, int expectedVersion);

    /** Lists the children of the node at the given path, or throws if there is no node at that path. */
    List<String> list(Path path);

    /** Creates and acquires a re-entrant lock with the given path. This blocks until the lock is acquired or timeout elapses. */
    AutoCloseable lock(Path path, Duration timeout) throws UncheckedTimeoutException;

    /** Data of a ZK node, including content (possibly empty, never {@code null}) and metadata. */
    record Data(Meta meta, byte[] data) { }

    /** Metadata for a ZK node. */
    record Meta(int version) { }

    /**
     * Register the singleton with the framework, so it may become active.
     * <p>
     * <strong>Call this after construction of the singleton, typically during component construction!</strong>
     * <ul>
     *   <li>If this activates the singleton, this happens synchronously, and any errors are propagated here.</li>
     *   <li>If this replaces an already active singleton, its deactivation is also called, prior to activation of this.</li>
     *   <li>If (de)activation is not complete within the given timeout, a timeout exception is thrown.</li>
     *   <li>If an error occurs (due to failed activation), unregistration is automatically attempted, with the same timeout.</li>
     * </ul>
     */
    void register(SingletonWorker singleton, Duration timeout);

    /**
     * Unregister with the framework, so this singleton may no longer be active, and returns
     * <p>
     * <strong>Call this before deconstruction of the singleton, typically during component deconstruction!</strong>
     * <ul>
     * <li>If this singleton is active, deactivation will be called synchronously, and errors propagated here.</li>
     * <li>If this also triggers activation of a new singleton, its activation is called after deactivation of this.</li>
     * <li>If (de)activation is not complete within the given timeout, a timeout exception is thrown.</li>
     * </ul>
     */
    void unregister(SingletonWorker singleton, Duration timeout);

    /**
     * Whether this container currently holds the exclusive lease for activation of singletons with this ID.
     */
    boolean isActive(String singletonId);

    /**
     * Callback interface for processes of which only a single instance should be active at any time, across all
     * containers in the cluster, and across all component generations.
     * <p>
     * <br>
     * Notes to implementors:
     * <ul>
     *     <li>{@link #activate()} is called by the system on a singleton whenever it is the newest registered
     *          singleton in this container, and this container has the lease for the ID with which the singleton
     *          was registered. See {@link #id}, {@link #register} and {@link #isActive}.</li>
     *     <li>{@link #deactivate()} is called by the system on a singleton which is currently active whenever
     *         the above no longer holds. See {@link #unregister}.</li>
     *     <li>Callbacks for the same ID are always invoked by the same thread, in serial; <strong>the callbacks must
     *         return in a timely manner</strong>, but are encouraged to throw exceptions when something's wrong.</li>
     *     <li>Activation and deactivation may be triggered by:
     *         <ol>
     *             <li>the container acquiring or losing the activation lease; or</li>
     *             <li>registration of unregistration of a new or obsolete singleton.</li>
     *         </ol>
     *         Events triggered by the latter happen synchronously, and errors are propagated to the caller for cleanup.
     *         Events triggered by the former may happen in the background, and because the system tries to always have
     *         one activated singleton, exceptions during activation will cause the container to abandon its lease, so
     *         another container may obtain it instead; exceptions during deactivation are only logged.
     *     </li>
     *     <li>A container without any registered singletons will not attempt to hold the activation lease.</li>
     * </ul>
     * <p>
     * <br>
     * Sample usage:
     * <pre>
     * public class SingletonHolder extends AbstractComponent {
     *
     *     private static final Duration timeout = Duration.ofSeconds(10);
     *     private final VespaCurator curator;
     *     private final SingletonWorker singleton;
     *
     *     public SingletonHolder(VespaCurator curator) {
     *         this.curator = curator;
     *         this.singleton = new Singleton();
     *         curator.register(singleton, timeout);
     *     }
     *
     *     &#064;Override
     *     public void deconstruct() {
     *         curator.unregister(singleton, timeout);
     *         singleton.shutdown();
     *     }
     *
     * }
     *
     * public class Singleton implements SingletonWorker {
     *
     *     private final SharedResource resource = ...; // Shared resource that requires exclusive access.
     *     private final ExecutorService executor = Executors.newSingleThreadExecutor();
     *     private final AtomicBoolean running = new AtomicBoolean();
     *     private Future&lt;?&gt; future = null;
     *
     *     &#064;Override
     *     public void activate() {
     *         try { resource.open(5, TimeUnit.SECONDS); } // Verify resource works here, and propagate any errors out.
     *         catch (Exception e) { resource.close(); throw new RuntimeException("failed opening " + resource, e); }
     *         running.set(true);
     *         future = executor.submit(this::doWork);
     *     }
     *
     *     &#064;Override
     *     public void deactivate() {
     *         running.set(false);
     *         try { future.get(5, TimeUnit.SECONDS); }
     *         catch (Exception e) { ... }
     *         finally { resource.close(); }
     *     }
     *
     *     private void doWork() {
     *         while (running.get()) { ... } // Regularly check whether we should keep running.
     *     }
     *
     *     public void shutdown() {
     *         executor.shutdownNow(); // Executor should have no running tasks at this point.
     *     }
     *
     * }
     * </pre>
     */
    interface SingletonWorker {

        /**
         * Called by the system whenever this singleton becomes the single active worker.
         * If this is triggered because the container obtains the activation lease, and activation fails,
         * then the container immediately releases the lease, so another container may acquire it instead.
         */
        void activate();

        /** Called by the system whenever this singleton is no longer the single active worker. */
        void deactivate();

        /**
         * The singleton ID to use when registering this with a {@link VespaCurator}.
         * At most one singleton worker with the given ID will be active, in the cluster, at any time.
         * {@link VespaCurator#isActive(String)} may be polled to see whether this container is currently
         * allowed to have an active singleton with the given ID.
         */
        default String id() { return getClass().getName(); }

    }

}
