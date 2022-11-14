// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import java.util.ArrayList;
import java.util.List;

/**
 * A class for multiple producers and potentially multiple consumers (usually
 * only one).
 *
 * <p>
 * The consuming threads always unregisters the data producers when doing
 * fetch(). This is the reason for having to do update through the directory.
 * The reason for this is otherwise, we would either get reference leaks from
 * registered objects belonging to dead threads if we did not unregister
 * instances, otherwise the sampling thread would have to unregister the
 * instance, and then we would create a memory relationship between all
 * producing threads, which is exactly what this class aims to avoid.
 * </p>
 *
 * <p>
 * A complete example from a test:
 * </p>
 *
 * <pre>
 * private static class SumUpdater implements ThreadLocalDirectory.Updater&lt;Integer, Integer&gt; {
 *
 *     {@literal @}Override
 *     public Integer update(Integer current, Integer x) {
 *         return Integer.valueOf(current.intValue() + x.intValue());
 *     }
 *
 *     {@literal @}Override
 *     public Integer createGenerationInstance(Integer previous) {
 *         return Integer.valueOf(0);
 *     }
 * }
 *
 * ... then the producers does (where r is in instance of
 * ThreadLocalDirectory)...
 *
 * {@literal @}Override
 * public void run() {
 *     LocalInstance&lt;Integer, Integer&gt; s = r.getLocalInstance();
 *     for (int i = 0; i &lt; 500; ++i) {
 *         r.update(Integer.valueOf(i), s);
 *     }
 * }
 *
 * ... and the consumer...
 *
 * List&lt;Integer&gt; measurements = s.fetch()
 * </pre>
 *
 * <p>
 * Invoking r.fetch() will produce a list of integers from all the participating
 * threads at any time.
 * </p>
 *
 * @param <AGGREGATOR> the type input data is aggregated into
 * @param <SAMPLE> the type of input data
 *
 * @author Steinar Knutsen
 */
public final class ThreadLocalDirectory<AGGREGATOR, SAMPLE> {

    /**
     * Factory interface to create the data container for each generation of
     * samples, and putting data into it.
     *
     * <p>
     * The method for actual insertion of a single sample into the current data
     * generation exists separate from LocalInstance.AGGREGATOR to make it
     * possible to use e.g. Integer and List as AGGREGATOR types.
     * </p>
     *
     * <p>
     * The allocation and sampling is placed in the same class, since you always
     * need to implement both.
     * </p>
     *
     * @param <AGGREGATOR> the type of the data container to produce
     * @param <SAMPLE> the type of the incoming data to store in the container.
     */
    public interface Updater<AGGREGATOR, SAMPLE> {

        /**
         * Create data container to receive produced data. This is invoked once
         * on every instance every time ThreadLocalDirectory.fetch() is invoked.
         * This might be an empty list, creating a new counter set to zero, or
         * even copying the current state of LocalInstance.current.
         * LocalInstance.current will be set to the value received from this
         * factory after invocation this method.
         *
         * <p>
         * The first time this method is invoked for a thread, previous will be
         * null.
         * </p>
         *
         * <p>
         * If using mutable objects, an implementation should always create a
         * new instance in this method, as the previous data generation will be
         * transmitted to the consuming thread. This obviously does not matter
         * if using immutable (value) objects.
         * </p>
         *
         * <p>
         * Examples:
         * </p>
         *
         * <p>
         * Using a mutable aggregator (a list of integers):
         * </p>
         *
         * <pre>
         * if (previous == null) {
         *     return new ArrayList&lt;Integer&gt;();
         * } else {
         *     return new ArrayList&lt;Integer&gt;(previous.size());
         * }
         * </pre>
         *
         * <p>
         * Using an immutable aggregator (an integer):
         * </p>
         *
         * <pre>
         * return Integer.valueOf(0);
         * </pre>
         *
         * @return a fresh structure to receive data
         */
        AGGREGATOR createGenerationInstance(AGGREGATOR previous);

        /**
         * Insert a data element of type S into the current generation of data
         * carrier T. This could be e.g. adding to a list, putting into a local
         * histogram or increasing a counter.
         *
         * <p>
         * The method may or may not return a fresh instance of the current
         * value for each invocation, if using a mutable aggregator the typical
         * case will be returning the same instance for the new and old value of
         * current, while if using an immutable aggregator, one is forced to
         * return new instances.
         * </p>
         *
         * <p>
         * Examples:
         * </p>
         *
         * <p>
         * Using a mutable aggregator (a list of instances of type SAMPLE):
         * </p>
         *
         * <pre>
         * current.add(x);
         * return current;
         * </pre>
         *
         * <p>
         * Using an immutable aggregator (Integer) while also using Integer as
         * type for SAMPLE:
         * </p>
         *
         * <pre>
         * return Integer.valueOf(current.intValue() + x.intValue());
         * </pre>
         *
         * @param current
         *            the current generation's data container
         * @param x
         *            the data to insert
         * @return the new current value, may be the same as previous
         */
        AGGREGATOR update(AGGREGATOR current, SAMPLE x);

    }

    /**
     * Implement this interface to be able to view the contents of a
     * ThreadLocalDirectory without resetting the local instances in each
     * thread.
     *
     * @param <AGGREGATOR> as for {@link Updater}
     * @param <SAMPLE> as for {@link Updater}
     * @see ThreadLocalDirectory#view()
     */
    public interface ObservableUpdater<AGGREGATOR, SAMPLE> extends Updater<AGGREGATOR, SAMPLE> {

        /**
         * Create an application specific copy of the AGGREGATOR for a thread.
         *
         * @param current
         *            the AGGREGATOR instance to copy
         * @return a copy of the incoming parameter
         */
        AGGREGATOR copy(AGGREGATOR current);

    }

    private final ThreadLocal<LocalInstance<AGGREGATOR, SAMPLE>> local = new ThreadLocal<>();
    private final Object directoryLock = new Object();
    private List<LocalInstance<AGGREGATOR, SAMPLE>> directory = new ArrayList<>();
    private final Updater<AGGREGATOR, SAMPLE> updater;
    private final ObservableUpdater<AGGREGATOR, SAMPLE> observableUpdater;

    public ThreadLocalDirectory(Updater<AGGREGATOR, SAMPLE> updater) {
        this.updater = updater;
        if (updater instanceof ObservableUpdater) {
            observableUpdater = (ObservableUpdater<AGGREGATOR, SAMPLE>) updater;
        } else {
            observableUpdater = null;
        }
    }

    private void put(LocalInstance<AGGREGATOR, SAMPLE> q) {
        // Has to set registered before adding to the list. Otherwise, the
        // instance might be removed from the list, set as unregistered, and
        // then the local thread might happily remove that information. The Java
        // memory model is a guarantee for the minimum amount of visibility,
        // not a definition of the actual amount.
        q.setRegistered(true);
        synchronized (directoryLock) {
            directory.add(q);
        }
    }

    /**
     * Fetch the current set of sampled data, and reset state of all thread
     * local instances. The producer threads will not alter data in the list
     * returned from this method.
     *
     * @return a list of data from all producer threads
     */
    public List<AGGREGATOR> fetch() {
        List<AGGREGATOR> contained;
        List<LocalInstance<AGGREGATOR, SAMPLE>> previous;
        int previousIntervalSize;

        synchronized (directoryLock) {
            previousIntervalSize = directory.size();
            previous = directory;
            directory = new ArrayList<>(
                    previousIntervalSize);
        }
        contained = new ArrayList<>(previousIntervalSize);
        // Yes, this is an inconsistence about when the registered state is
        // reset and when the thread local is removed from the list.
        // LocalInstance.isRegistered tells whether the data is available to
        // some consumer, not whether the LocalInstance is a member of the
        // directory.
        for (LocalInstance<AGGREGATOR, SAMPLE> x : previous) {
            contained.add(x.getAndReset(updater));
        }
        return contained;
    }

    /**
     * Get a view of the current data. This requires this ThreadLocalDirectory
     * to have been instantiated with an updater implementing ObservableUpdater.
     *
     * @return a list of a copy of the current data in all producer threads
     * @throws IllegalStateException if the updater does not implement {@link ObservableUpdater}
     */
    public List<AGGREGATOR> view() {
        if (observableUpdater == null) {
            throw new IllegalStateException("Does not use observable updaters.");
        }
        List<LocalInstance<AGGREGATOR, SAMPLE>> current;
        List<AGGREGATOR> view;
        synchronized (directoryLock) {
            current = new ArrayList<>(
                    directory);
        }
        view = new ArrayList<>(current.size());
        for (LocalInstance<AGGREGATOR, SAMPLE> x : current) {
            view.add(x.copyCurrent(observableUpdater));
        }
        return view;
    }

    private LocalInstance<AGGREGATOR, SAMPLE> getOrCreateLocal() {
        LocalInstance<AGGREGATOR, SAMPLE> current = local.get();
        if (current == null) {
            current = new LocalInstance<>(updater);
            local.set(current);
        }
        return current;
    }

    /**
     * Expose the thread local for the running thread, for use in conjunction
     * with update(SAMPLE, LocalInstance&lt;AGGREGATOR, SAMPLE&gt;).
     *
     * @return the current thread's local instance
     */
    public LocalInstance<AGGREGATOR, SAMPLE> getLocalInstance() {
        return getOrCreateLocal();
    }

    /**
     * Input data from a producer thread.
     *
     * @param x the data to insert
     */
    public void update(SAMPLE x) {
        update(x, getOrCreateLocal());
    }

    /**
     * Update a value with a given thread local instance.
     *
     * <p>
     * If a producer thread is to insert a series of data, it is desirable to
     * limit the number of memory transactions to the theoretical minimum. Since
     * reading a thread local is the memory equivalence of reading a volatile,
     * it is then useful to avoid re-reading the running threads' input
     * instance. For this scenario, fetch the running thread's instance with
     * getLocalInstance(), and then insert the produced data with the multiple
     * calls necessary to update(SAMPLE, LocalInstance&lt;AGGREGATOR, SAMPLE&gt;).
     * </p>
     *
     * @param x the data to insert
     * @param localInstance the local data insertion instance
     */
    public void update(SAMPLE x, LocalInstance<AGGREGATOR, SAMPLE> localInstance) {
        boolean isRegistered;
        isRegistered = localInstance.update(x, updater);
        if (!isRegistered) {
            put(localInstance);
        }
    }

}
