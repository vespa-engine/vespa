// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.inject.Key;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.SharedResource;
import com.yahoo.jdisc.References;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>This is a utility class to help manage {@link SharedResource}s while configuring a {@link ContainerBuilder}. This
 * class can still be used without a ContainerBuilder, albeit with the injection APIs (i.e. {@link #get(Class)} and
 * {@link #get(com.google.inject.Key)}) disabled.</p>
 * <p>The core problem with SharedResources is that they need to be tracked carefully to ensure exception safety in the
 * code that creates and registers them with a ContainerBuilder. The code for this typically looks like this:</p>
 * <pre>
 * MyServerProvider serverProvider = null;
 * MyRequestHandler requestHandler = null;
 * try {
 *     serverProvider = builder.getInstance(MyServerProvider.class);
 *     serverProvider.start();
 *     containerBuilder.serverProviders().install(serverProvider);
 *
 *     requestHandler = builder.getInstance(MyRequestHandler.class);
 *     containerBuilder.serverBindings().bind("http://host/path", requestHandler);
 *
 *     containerActivator.activateContainer(containerBuilder);
 * } finally {
 *     if (serverProvider != null) {
 *         serverProvider.release();
 *     }
 *     if (requestHandler != null) {
 *         requestHandler.release();
 *     }
 * }
 * </pre>
 *
 * <p>The ResourcePool helps remove the boiler-plate code used to track the resources from outside the try-finally
 * block. Using the ResourcePool, the above snippet can be rewritten to the following:</p>
 * <pre>
 * try (ResourcePool resources = new ResourcePool(containerBuilder)) {
 *     ServerProvider serverProvider = resources.get(MyServerProvider.class);
 *     serverProvider.start();
 *     containerBuilder.serverProviders().install(serverProvider);
 *
 *     RequestHandler requestHandler = resources.get(MyRequestHandler.class);
 *     containerBuilder.serverBindings().bind("http://host/path", requestHandler);
 *
 *     containerActivator.activateContainer(containerBuilder);
 * }
 * </pre>
 *
 * <p>This class is not thread-safe.</p>
 *
 * @author Simon Thoresen Hult
 */
public final class ResourcePool extends AbstractResource implements AutoCloseable {

    private final List<ResourceReference> resources = new ArrayList<>();
    private final ContainerBuilder builder;

    /**
     * <p>Creates a new instance of this class without a backing {@link ContainerBuilder}. A ResourcePool created with
     * this constructor will throw a NullPointerException if either {@link #get(Class)} or {@link #get(Key)} is
     * called.</p>
     */
    public ResourcePool() {
        this(null);
    }

    /**
     * <p>Creates a new instance of this class. All calls to {@link #get(Class)} and {@link #get(Key)} are forwarded to
     * the {@link ContainerBuilder} given to this constructor.</p>
     *
     * @param builder The ContainerBuilder that provides the injection functionality for this ResourcePool.
     */
    public ResourcePool(ContainerBuilder builder) {
        this.builder = builder;
    }

    /**
     * <p>Adds the given {@link SharedResource} to this ResourcePool. Note that this DOES NOT call {@link
     * SharedResource#refer()}, as opposed to {@link #retain(SharedResource)}. When this ResourcePool is
     * destroyed, it will release the main reference to the resource (by calling {@link SharedResource#release()}).</p>
     *
     * @param t   The SharedResource to add.
     * @param <T> The class of parameter <tt>t</tt>.
     * @return The parameter <tt>t</tt>, to allow inlined calls to this function.
     */
    public <T extends SharedResource> T add(T t) {
        try {
            resources.add(References.fromResource(t));
        } catch (IllegalStateException e) {
            // Ignore. TODO(bakksjo): Don't rely on ISE to detect duplicates; handle that in this class instead.
        }
        return t;
    }

    /**
     * <p>Returns the appropriate instance for the given injection key. Note that this DOES NOT call {@link
     * SharedResource#refer()}. This is the equivalent of doing:</p>
     * <pre>
     * t = containerBuilder.getInstance(key);
     * resourcePool.add(t);
     * </pre>
     *
     * <p>When this ResourcePool is destroyed, it will release the main reference to the resource
     * (by calling {@link SharedResource#release()}).</p>
     *
     * @param key The injection key to return.
     * @param <T> The class of the injection type.
     * @return The appropriate instance of T.
     * @throws NullPointerException If this pool was constructed without a ContainerBuilder.
     */
    public <T extends SharedResource> T get(Key<T> key) {
        return add(builder.getInstance(key));
    }

    /**
     * <p>Returns the appropriate instance for the given injection type. Note that this DOES NOT call {@link
     * SharedResource#refer()}. This is the equivalent of doing:</p>
     * <pre>
     * t = containerBuilder.getInstance(type);
     * resourcePool.add(t);
     * </pre>
     *
     * <p>When this ResourcePool is destroyed, it will release the main reference to the resource
     * (by calling {@link SharedResource#release()}).</p>
     *
     * @param type The injection type to return.
     * @param <T>  The class of the injection type.
     * @return The appropriate instance of T.
     * @throws NullPointerException If this pool was constructed without a ContainerBuilder.
     */
    public <T extends SharedResource> T get(Class<T> type) {
        return add(builder.getInstance(type));
    }

    /**
     * <p>Retains and adds the given {@link SharedResource} to this ResourcePool. Note that this DOES call {@link
     * SharedResource#refer()}, as opposed to {@link #add(SharedResource)}.
     *
     * <p>When this ResourcePool is destroyed, it will release the resource reference returned by the
     * {@link SharedResource#refer()} call.</p>
     *
     * @param t   The SharedResource to retain and add.
     * @param <T> The class of parameter <tt>t</tt>.
     * @return The parameter <tt>t</tt>, to allow inlined calls to this function.
     */
    public <T extends SharedResource> T retain(T t) {
        resources.add(t.refer());
        return t;
    }

    @Override
    protected void destroy() {
        for (ResourceReference resource : resources) {
            resource.close();
        }
    }

    @Override
    public void close() throws Exception {
        release();
    }
}
