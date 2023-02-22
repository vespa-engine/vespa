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
import java.util.stream.Collectors;

/**
 * A host registry with a mapping between hostname and ApplicationId
 *
 * @author Ulf Lilleengen
 */
public class HostRegistry implements HostValidator {

    private final Map<String, ApplicationId> host2ApplicationId = new ConcurrentHashMap<>();

    public ApplicationId getApplicationId(String hostName) {
        return host2ApplicationId.get(hostName);
    }

    public synchronized void update(ApplicationId applicationId, Collection<String> newHosts) {
        verifyHosts(applicationId, newHosts);
        Collection<String> removedHosts = findRemovedHosts(newHosts, getHosts(applicationId));
        removeHosts(removedHosts);
        addHosts(applicationId, newHosts);
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

    public synchronized void removeHosts(ApplicationId applicationId) {
        host2ApplicationId.entrySet().removeIf(entry -> entry.getValue().equals(applicationId));
    }

    public synchronized void removeHosts(TenantName tenantName) {
        host2ApplicationId.entrySet().removeIf(entry -> entry.getValue().tenant().equals(tenantName));
    }

    public synchronized Collection<String> getAllHosts() {
        return Collections.unmodifiableCollection(new ArrayList<>(host2ApplicationId.keySet()));
    }

    public synchronized Collection<String> getHosts(ApplicationId applicationId) {
        return host2ApplicationId.entrySet().stream()
                                 .filter(entry -> entry.getValue().equals(applicationId))
                                 .map(Map.Entry::getKey)
                                 .collect(Collectors.toSet());
    }

    private boolean hostAlreadyTaken(String host, ApplicationId applicationId) {
        return host2ApplicationId.containsKey(host) && !applicationId.equals(host2ApplicationId.get(host));
    }

    private static Collection<String> findRemovedHosts(Collection<String> newHosts, Collection<String> previousHosts) {
        return Collections2.filter(previousHosts, host -> !newHosts.contains(host));
    }

    private void removeHosts(Collection<String> hosts) {
        for (String host : hosts) {
            host2ApplicationId.remove(host);
        }
    }

    private void addHosts(ApplicationId key, Collection<String> hosts) {
        for (String host : hosts) {
            host2ApplicationId.put(host, key);
        }
    }

}
