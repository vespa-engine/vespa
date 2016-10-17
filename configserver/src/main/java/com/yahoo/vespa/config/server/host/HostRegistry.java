// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.host;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.yahoo.log.LogLevel;

/**
 * A host registry that create mappings between some type T and a list of hosts, represented as
 * strings.
 * TODO: Maybe we should have a Host type, but using String for now.
 * TODO: Is there a generalized version of this pattern? Need some sort mix of Bimap and Multimap
 *
 * @author lulf
 * @since 5.3
 */
public class HostRegistry<T> implements HostValidator<T> {

    private static final Logger log = Logger.getLogger(HostRegistry.class.getName());

    private final Map<T, Collection<String>> key2HostsMap = new ConcurrentHashMap<>();
    private final Map<String, T> host2KeyMap = new ConcurrentHashMap<>();

    public T getKeyForHost(String hostName) {
        return host2KeyMap.get(hostName);
    }

    public void update(T key, Collection<String> newHosts) {
        verifyHosts(key, newHosts);
        log.log(LogLevel.DEBUG, "Setting hosts for key(" + key + "), newHosts(" + newHosts + "), " +
                                "currentHosts(" + getCurrentHosts(key) + ")");
        Collection<String> removedHosts = getRemovedHosts(newHosts, getCurrentHosts(key));
        removeHosts(removedHosts);
        addHosts(key, newHosts);
    }

    public void verifyHosts(T key, Collection<String> newHosts) {
        for (String host : newHosts) {
            if (hostAlreadyTaken(host, key)) {
                throw new IllegalArgumentException("'" + key + "' tried to allocate host '" + host + 
                                                   "', but the host is already taken by '" + host2KeyMap.get(host) + "'");
            }
        }
    }

    public void removeHostsForKey(T key) {
        for (Iterator<Map.Entry<T, Collection<String>>> it = key2HostsMap.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<T, Collection<String>> entry = it.next();
            if (entry.getKey().equals(key)) {
                Collection<String> hosts = entry.getValue();
                it.remove();
                removeHosts(hosts);
            }
        }
    }

    public Collection<String> getAllHosts() {
        return Collections.unmodifiableCollection(new ArrayList<>(host2KeyMap.keySet()));
    }

    Collection<String> getCurrentHosts(T key) {
        return key2HostsMap.containsKey(key) ? new ArrayList<>(key2HostsMap.get(key)) : new ArrayList<String>();
    }

    private boolean hostAlreadyTaken(String host, T key) {
        return host2KeyMap.containsKey(host) && !key.equals(host2KeyMap.get(host));
    }

    private static Collection<String> getRemovedHosts(final Collection<String> newHosts, Collection<String> previousHosts) {
        return Collections2.filter(previousHosts, new Predicate<String>() {
            @Override
            public boolean apply(String host) {
                return !newHosts.contains(host);
            }
        });
    }

    private void removeHosts(Collection<String> removedHosts) {
        for (String host : removedHosts) {
            log.log(LogLevel.DEBUG, "Removing " + host);
            host2KeyMap.remove(host);
        }
    }

    private void addHosts(T key, Collection<String> newHosts) {
        for (String host : newHosts) {
            log.log(LogLevel.DEBUG, "Adding " + host);
            host2KeyMap.put(host, key);
        }
        key2HostsMap.put(key, new ArrayList<>(newHosts));
    }

}
