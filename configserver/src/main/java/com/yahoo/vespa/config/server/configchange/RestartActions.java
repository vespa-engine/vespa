// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ServiceInfo;

import java.util.*;

/**
 * Represents all actions to restart services in order to handle a config change.
 *
 * @author geirst
 */
public class RestartActions {

    public static class Entry {

        private final String clusterName;
        private final String clusterType;
        private final String serviceType;
        private final Set<ServiceInfo> services = new LinkedHashSet<>();
        private final Set<String> messages = new TreeSet<>();

        private Entry addService(ServiceInfo service) {
            services.add(service);
            return this;
        }

        private Entry addMessage(String message) {
            messages.add(message);
            return this;
        }

        private Entry(String clusterName, String clusterType, String serviceType) {
            this.clusterName = clusterName;
            this.clusterType = clusterType;
            this.serviceType = serviceType;
        }

        public String getClusterName() {
            return clusterName;
        }

        public String getClusterType() {
            return clusterType;
        }

        public String getServiceType() {
            return serviceType;
        }

        public Set<ServiceInfo> getServices() {
            return services;
        }

        public Set<String> getMessages() {
            return messages;
        }

    }

    private Entry addEntry(ServiceInfo service) {
        String clusterName = service.getProperty("clustername").orElse("");
        String clusterType = service.getProperty("clustertype").orElse("");
        String entryId = clusterType + "." + clusterName + "." + service.getServiceType();
        Entry entry = actions.get(entryId);
        if (entry == null) {
            entry = new Entry(clusterName, clusterType, service.getServiceType());
            actions.put(entryId, entry);
        }
        return entry;
    }

    private final Map<String, Entry> actions = new TreeMap<>();

    public RestartActions() {
    }

    public RestartActions(List<ConfigChangeAction> actions) {
        for (ConfigChangeAction action : actions) {
            if (action.getType().equals(ConfigChangeAction.Type.RESTART)) {
                for (ServiceInfo service : action.getServices()) {
                    addEntry(service).
                            addService(service).
                            addMessage(action.getMessage());
                }
            }
        }
    }

    public List<Entry> getEntries() {
        return new ArrayList<>(actions.values());
    }

    public String format() {
        return new RestartActionsFormatter(this).format();
    }

    public boolean isEmpty() {
        return actions.isEmpty();
    }
}
