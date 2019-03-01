// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.host;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.common.collect.Collections2;
import com.yahoo.log.LogLevel;

/**
 * A host registry with a mapping between hosts (hostname as a String) and some type T
 * TODO: Maybe we should have a Host type, but using String for now.
 *
 * @author Ulf Lilleengen
 */
public class HostRegistry<T> implements HostValidator<T> {

    private static final Logger log = Logger.getLogger(HostRegistry.class.getName());

    private final Map<String, T> host2KeyMap = new ConcurrentHashMap<>();

    public T getKeyForHost(String hostName) {
        return host2KeyMap.get(hostName);
    }

    public synchronized void update(T key, Collection<String> newHosts) {
        verifyHosts(key, newHosts);
        Collection<String> currentHosts = getHostsForKey(key);
        log.log(LogLevel.DEBUG, () -> "Setting hosts for key '" + key + "', " +
                "newHosts: " + newHosts + ", " +
                "currentHosts: " + currentHosts);
        Collection<String> removedHosts = getRemovedHosts(newHosts, currentHosts);
        removeHosts(removedHosts);
        addHosts(key, newHosts);
    }

    public synchronized void verifyHosts(T key, Collection<String> newHosts) {
        for (String host : newHosts) {
            if (hostAlreadyTaken(host, key)) {
                throw new IllegalArgumentException("'" + key + "' tried to allocate host '" + host + 
                                                   "', but the host is already taken by '" + host2KeyMap.get(host) + "'");
            }
        }
    }

    public synchronized void removeHostsForKey(T key) {
        host2KeyMap.entrySet().removeIf(entry -> entry.getValue().equals(key));
    }

    public synchronized Collection<String> getAllHosts() {
        return Collections.unmodifiableCollection(new ArrayList<>(host2KeyMap.keySet()));
    }

    synchronized Collection<String> getHostsForKey(T key) {
        return host2KeyMap.entrySet().stream()
                .filter(entry -> entry.getValue().equals(key))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private boolean hostAlreadyTaken(String host, T key) {
        return host2KeyMap.containsKey(host) && !key.equals(host2KeyMap.get(host));
    }

    private static Collection<String> getRemovedHosts(Collection<String> newHosts, Collection<String> previousHosts) {
        return Collections2.filter(previousHosts, host -> !newHosts.contains(host));
    }

    private void removeHosts(Collection<String> removedHosts) {
        for (String host : removedHosts) {
            log.log(LogLevel.DEBUG, () -> "Removing " + host);
            host2KeyMap.remove(host);
        }
    }

    private void addHosts(T key, Collection<String> newHosts) {
        for (String host : newHosts) {
            log.log(LogLevel.DEBUG, () -> "Adding " + host);
            host2KeyMap.put(host, key);
        }
    }

}
