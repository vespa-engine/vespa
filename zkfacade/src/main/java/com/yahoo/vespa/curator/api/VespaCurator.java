// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.api;

import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.path.Path;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
     * Register the singleton with the framework, so it may become active, and returns a
     * synchronisation handle to any deactivations or activations triggered by this.
     * If there is already another active singleton with the given ID (in this JVM),
     * that will be deactivated before the new one is activated.
     */
    Future<?> registerSingleton(String singletonId, SingletonWorker singleton);

    /**
     * Unregister with the framework, so this singleton may no longer be active, and returns
     * a synchronisation handle to any deactivation or activation triggered by this.
     * If this is the last singleton registered with its ID, then this container immediately releases
     * the activation lease for that ID, so another container may acquire it.
     */
    Future<?> unregisterSingleton(SingletonWorker singleton);

    /**
     * Whether this container currently holds the exclusive lease for activation of singletons with this ID.
     */
    boolean isActive(String singletonId);

    /**
     * Callback interface for processes of which only a single instance should be active at any time, across all
     * containers in the cluster, and across all component generations. Notes:
     * <ul>
     *     <li>{@link #activate()} is called by the system on a singleton whenever it is the newest registered
     *          singleton in this container, and this container has the lease for the ID with which the singleton
     *          was registered. See {@link #registerSingleton} and {@link #isActive}.</li>
     *     <li>{@link #deactivate()} is called by the system on a singleton which is currently active whenever
     *         the above no longer holds. See {@link #unregisterSingleton}.</li>
     *     <li>Callbacks for the same ID are always invoked by the same thread, in serial;
     *         the callbacks must return in a timely manner, but are allowed to throw exceptions.</li>
     *     <li>Activation and deactivation may be triggered by:
     *         <ol><li>the container acquiring or losing the activation lease; or</li>
     *         <li>registration of unregistration of a new or obsolete singleton.</li></ol>
     *         Events triggered by the latter happen synchronously, and errors are propagated to the caller for cleanup.
     *         Events triggered by the former may happen in the background, and because the system tries to always have
     *         one activated singleton, exceptions during activation will cause the container to abandon its lease, so
     *         another container may obtain it instead; exceptions during deactivation are only logged.
     *     </li>
     *     <li>A container without any registered singletons will not attempt to hold the activation lease.</li>
     * </ul>
     * See {@link AbstractSingletonWorker} for an abstract superclass to use for implementations.
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

    }

}
