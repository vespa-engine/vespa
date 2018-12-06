// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.ApplicationId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A non-thread-safe mutable container of ApplicationInfo in the DuperModel, also taking care of listeners on changes.
 *
 * @author hakonhall
 */
public class DuperModel {
    private final Map<ApplicationId, ApplicationInfo> applications = new TreeMap<>();
    private final List<DuperModelListener> listeners = new ArrayList<>();

    public void registerListener(DuperModelListener listener) {
        applications.values().forEach(listener::applicationActivated);
        listeners.add(listener);
    }

    public boolean contains(ApplicationId applicationId) {
        return applications.containsKey(applicationId);
    }

    public void add(ApplicationInfo applicationInfo) {
        applications.put(applicationInfo.getApplicationId(), applicationInfo);
        listeners.forEach(listener -> listener.applicationActivated(applicationInfo));
    }

    public void remove(ApplicationId applicationId) {
        if (applications.remove(applicationId) != null) {
            listeners.forEach(listener -> listener.applicationRemoved(applicationId));
        }
    }

    public List<ApplicationInfo> getApplicationInfos() {
        return Collections.unmodifiableList(new ArrayList<>(applications.values()));
    }
}
