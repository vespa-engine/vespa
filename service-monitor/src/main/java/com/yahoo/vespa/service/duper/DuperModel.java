// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.service.monitor.DuperModelListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * A non-thread-safe mutable container of ApplicationInfo, also taking care of listeners on changes.
 *
 * @author hakonhall
 */
public class DuperModel {
    private static Logger logger = Logger.getLogger(DuperModel.class.getName());

    private final Map<ApplicationId, ApplicationInfo> applications = new TreeMap<>();
    private final List<DuperModelListener> listeners = new ArrayList<>();
    private boolean isComplete = false;

    public void registerListener(DuperModelListener listener) {
        applications.values().forEach(listener::applicationActivated);
        listeners.add(listener);
    }

    public void setCompleteness(boolean isComplete) { this.isComplete = isComplete; }
    public boolean isComplete() { return isComplete; }

    public boolean contains(ApplicationId applicationId) {
        return applications.containsKey(applicationId);
    }

    public void add(ApplicationInfo applicationInfo) {
        applications.put(applicationInfo.getApplicationId(), applicationInfo);
        logger.log(LogLevel.DEBUG, "Added " + applicationInfo.getApplicationId());
        listeners.forEach(listener -> listener.applicationActivated(applicationInfo));
    }

    public void remove(ApplicationId applicationId) {
        if (applications.remove(applicationId) != null) {
            logger.log(LogLevel.DEBUG, "Removed " + applicationId);
            listeners.forEach(listener -> listener.applicationRemoved(applicationId));
        }
    }

    public Optional<ApplicationInfo> getApplicationInfo(ApplicationId applicationId) {
        return Optional.ofNullable(applications.get(applicationId));
    }

    public List<ApplicationInfo> getApplicationInfos() {
        logger.log(LogLevel.DEBUG, "Applications in duper model: " + applications.values().size());
        return List.copyOf(applications.values());
    }
}
