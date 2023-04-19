// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import ai.vespa.http.DomainName;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.provision.ApplicationId;
import java.util.logging.Level;
import com.yahoo.vespa.service.monitor.DuperModelListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A non-thread-safe mutable container of ApplicationInfo, also taking care of listeners on changes.
 *
 * @author hakonhall
 */
public class DuperModel {
    private static Logger logger = Logger.getLogger(DuperModel.class.getName());

    private final Map<ApplicationId, ApplicationInfo> applicationsById = new HashMap<>();
    private final Map<DomainName, ApplicationId> idsByHostname = new HashMap<>();
    private final Map<ApplicationId, HashSet<DomainName>> hostnamesById = new HashMap<>();

    private final List<DuperModelListener> listeners = new ArrayList<>();
    private boolean isComplete = false;

    public void registerListener(DuperModelListener listener) {
        applicationsById.values().forEach(listener::applicationActivated);
        if (isComplete) {
            listener.bootstrapComplete();
        }
        listeners.add(listener);
    }

    void setComplete() {
        if (!isComplete) {
            logger.log(Level.FINE, "All applications have been activated: duper model is complete");
            isComplete = true;

            listeners.forEach(DuperModelListener::bootstrapComplete);
        }
    }

    public boolean isComplete() { return isComplete; }

    public int numberOfApplications() {
        return applicationsById.size();
    }

    public int numberOfHosts() {
        return idsByHostname.size();
    }

    public boolean contains(ApplicationId applicationId) {
        return applicationsById.containsKey(applicationId);
    }

    public Optional<ApplicationInfo> getApplicationInfo(ApplicationId applicationId) {
        return Optional.ofNullable(applicationsById.get(applicationId));
    }

    public Optional<ApplicationInfo> getApplicationInfo(DomainName hostName) {
        return Optional.ofNullable(idsByHostname.get(hostName)).map(applicationsById::get);
    }

    public List<ApplicationInfo> getApplicationInfos() {
        return List.copyOf(applicationsById.values());
    }

    /** Note: Returns an empty set for unknown application. */
    public Set<DomainName> getHostnames(ApplicationId applicationId) {
        HashSet<DomainName> set = hostnamesById.get(applicationId);
        return set == null ? Set.of() : Set.copyOf(set);
    }

    public Optional<ApplicationId> getApplicationId(DomainName hostname) {
        return Optional.ofNullable(idsByHostname.get(hostname));
    }

    public void add(ApplicationInfo applicationInfo) {
        ApplicationId id = applicationInfo.getApplicationId();
        ApplicationInfo oldApplicationInfo = applicationsById.put(id, applicationInfo);

        final String logPrefix;
        if (oldApplicationInfo == null) {
            logPrefix = isComplete ? "New application " : "Bootstrapped application ";
        } else {
            logPrefix = isComplete ? "Reactivated application " : "Rebootstrapped application ";
        }
        logger.log(Level.FINE, () -> logPrefix + id.toFullString());

        updateHostnameVsIdMaps(applicationInfo, id);

        listeners.forEach(listener -> listener.applicationActivated(applicationInfo));
    }

    public void remove(ApplicationId applicationId) {
        Set<DomainName> hostnames = hostnamesById.remove(applicationId);
        if (hostnames != null) {
            hostnames.forEach(idsByHostname::remove);
        }

        ApplicationInfo application = applicationsById.remove(applicationId);
        if (application != null) {
            logger.log(Level.FINE, () -> "Removed application " + applicationId.toFullString());
            listeners.forEach(listener -> listener.applicationRemoved(applicationId));
        }
    }

    /** Update hostnamesById and idsByHostname based on a new applicationInfo. */
    private void updateHostnameVsIdMaps(ApplicationInfo applicationInfo, ApplicationId id) {
        Set<DomainName> removedHosts = new HashSet<>(hostnamesById.computeIfAbsent(id, k -> new HashSet<>()));

        applicationInfo.getModel().getHosts().stream()
                .map(HostInfo::getHostname)
                .map(DomainName::of)
                .forEach(hostname -> {
                    if (!removedHosts.remove(hostname)) {
                        // hostname has been added
                        hostnamesById.get(id).add(hostname);
                        ApplicationId previousId = idsByHostname.put(hostname, id);

                        if (previousId != null && !previousId.equals(id)) {
                            // If an activation contains a host that is currently assigned to a
                            // different application we will patch up our data structures to remain
                            // internally consistent. But listeners may be fooled.
                            logger.log(Level.WARNING, hostname + " has been reassigned from " +
                                    previousId.toFullString() + " to " + id.toFullString());

                            Set<DomainName> previousHostnames = hostnamesById.get(previousId);
                            if (previousHostnames != null) {
                                previousHostnames.remove(hostname);
                            }
                        }
                    }
                });

        removedHosts.forEach(hostname -> {
            // hostname has been removed
            idsByHostname.remove(hostname);
            hostnamesById.get(id).remove(hostname);
        });
    }
}
