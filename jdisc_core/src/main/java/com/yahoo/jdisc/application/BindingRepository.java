// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.handler.RequestHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * <p>This is a mutable repository of bindings from {@link UriPattern}s to some target type T. The {@link
 * ContainerBuilder} has a mapping of named instances of this class for {@link RequestHandler}s, and is used to
 * configure the set of {@link BindingSet}s that eventually become part of the active {@link Container}.</p>
 *
 * @author Simon Thoresen Hult
 */
public class BindingRepository<T> implements Iterable<Map.Entry<UriPattern, T>> {

    private static final Logger log = Logger.getLogger(BindingRepository.class.getName());

    private final Map<UriPattern, T> bindings = new HashMap<>();

    /**
     * <p>Creates a {@link UriPattern} from the given pattern string, and calls {@link #put(UriPattern, Object)}.</p>
     *
     * @param uriPattern The URI pattern to parse and bind to the target.
     * @param target     The target to assign to the URI pattern.
     * @throws NullPointerException     If any argument is null.
     * @throws IllegalArgumentException If the URI pattern string could not be parsed.
     */
    public void bind(String uriPattern, T target) {
        put(new UriPattern(uriPattern), target);
    }

    /**
     * <p>Convenient method for calling {@link #bind(String, Object)} for all entries in a collection of bindings.</p>
     *
     * @param bindings The collection of bindings to copy to this.
     * @throws NullPointerException If argument is null or contains null.
     */
    public void bindAll(Map<String, T> bindings) {
        for (Map.Entry<String, T> entry : bindings.entrySet()) {
            bind(entry.getKey(), entry.getValue());
        }
    }

    /**
     * <p>Binds the given target to the given {@link UriPattern}. Although all bindings will eventually be evaluated by
     * a call to {@link BindingSet#resolve(URI)}, where matching order is significant, the order in which bindings are
     * added is NOT. Instead, the creation of the {@link BindingSet} in {@link #activate()} sorts the bindings in such a
     * way that the more strict patterns are evaluated first. See class-level commentary on {@link UriPattern} for more
     * on this.
     *
     * @param uriPattern The URI pattern to parse and bind to the target.
     * @param target     The target to assign to the URI pattern.
     * @throws NullPointerException     If any argument is null.
     * @throws IllegalArgumentException If the pattern has already been bound to another target.
     */
    public void put(UriPattern uriPattern, T target) {
        Objects.requireNonNull(uriPattern, "uriPattern");
        Objects.requireNonNull(target, "target");
        if (bindings.containsKey(uriPattern)) {
            T boundTarget = bindings.get(uriPattern);
            log.info("Pattern '" + uriPattern + "' was already bound to target of class " + boundTarget.getClass().getName()
                             + ", and will NOT be bound to target of class " + target.getClass().getName());
        } else {
            bindings.put(uriPattern, target);
        }
    }

    /**
     * <p>Convenient method for calling {@link #put(UriPattern, Object)} for all entries in a collection of
     * bindings.</p>
     *
     * @param bindings The collection of bindings to copy to this.
     * @throws NullPointerException If argument is null or contains null.
     */
    public void putAll(Iterable<Map.Entry<UriPattern, T>> bindings) {
        for (Map.Entry<UriPattern, T> entry : bindings) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * <p>Creates and returns an immutable {@link BindingSet} that contains the bindings of this BindingRepository.
     * Notice that the BindingSet uses a snapshot of the current bindings so that this repository remains mutable and
     * reusable.</p>
     *
     * @return The created BindingSet instance.
     */
    public BindingSet<T> activate() {
        return new BindingSet<>(bindings.entrySet());
    }

    /**
     * Removes all bindings from this repository.
     */
    public void clear() {
        bindings.clear();
    }

    @Override
    public Iterator<Map.Entry<UriPattern, T>> iterator() {
        return bindings.entrySet().iterator();
    }
}
