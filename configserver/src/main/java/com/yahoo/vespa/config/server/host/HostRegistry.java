// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.host;

import com.google.common.collect.Collections2;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A host registry with a mapping between hosts (hostname as a String) and some type T
 * TODO: Maybe we should have a Host type, but using String for now.
 *
 * @author Ulf Lilleengen
 */
public class HostRegistry implements HostValidator {

    private static final Logger log = Logger.getLogger(HostRegistry.class.getName());

    private final Map<String, ApplicationId> host2ApplicationId = new ConcurrentHashMap<>();

    public ApplicationId getApplicationId(String hostName) {
        return host2ApplicationId.get(hostName);
    }

    public synchronized void update(ApplicationId key, Collection<String> newHosts) {
        verifyHosts(key, newHosts);
        Collection<String> currentHosts = getHosts(key);
        log.log(Level.FINE, () -> "Setting hosts for key '" + key + "', " +
                                  "newHosts: " + newHosts + ", " +
                                  "currentHosts: " + currentHosts);
        Collection<String> removedHosts = findRemovedHosts(newHosts, currentHosts);
        removeHosts(removedHosts);
        addHosts(key, newHosts);
    }

    @Override
    public synchronized void verifyHosts(ApplicationId applicationId, Collection<String> newHosts) {
        for (String host : newHosts) {
            if (hostAlreadyTaken(host, applicationId)) {
                throw new IllegalArgumentException("'" + applicationId + "' tried to allocate host '" + host +
                                                   "', but the host is already taken by '" + host2ApplicationId.get(host) + "'");
            }
        }
    }

    public synchronized void removeHosts(ApplicationId key) {
        host2ApplicationId.entrySet().removeIf(entry -> entry.getValue().equals(key));
    }

    public synchronized void removeHosts(TenantName key) {
        host2ApplicationId.entrySet().removeIf(entry -> entry.getValue().tenant().equals(key));
    }

    public synchronized Collection<String> getAllHosts() {
        return Collections.unmodifiableCollection(new ArrayList<>(host2ApplicationId.keySet()));
    }

    public synchronized Collection<String> getHosts(ApplicationId key) {
        return host2ApplicationId.entrySet().stream()
                                 .filter(entry -> entry.getValue().equals(key))
                                 .map(Map.Entry::getKey)
                                 .collect(Collectors.toSet());
    }

    private boolean hostAlreadyTaken(String host, ApplicationId key) {
        return host2ApplicationId.containsKey(host) && !key.equals(host2ApplicationId.get(host));
    }

    private static Collection<String> findRemovedHosts(Collection<String> newHosts, Collection<String> previousHosts) {
        return Collections2.filter(previousHosts, host -> !newHosts.contains(host));
    }

    private void removeHosts(Collection<String> removedHosts) {
        for (String host : removedHosts) {
            log.log(Level.FINE, () -> "Removing " + host);
            host2ApplicationId.remove(host);
        }
    }

    private void addHosts(ApplicationId key, Collection<String> newHosts) {
        for (String host : newHosts) {
            log.log(Level.FINE, () -> "Adding " + host);
            host2ApplicationId.put(host, key);
        }
    }

}
