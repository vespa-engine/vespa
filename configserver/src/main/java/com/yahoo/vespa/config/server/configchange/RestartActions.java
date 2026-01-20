// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ConfigChangeRestartAction;
import com.yahoo.config.model.api.ServiceInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

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
        private final boolean ignoreForInternalRedeploy;
        private final boolean deferChanges;
        private final Set<ServiceInfo> services = new LinkedHashSet<>();
        private final Set<String> messages = new TreeSet<>();

        private Entry addService(ServiceInfo service) {
            services.add(service);
            return this;
        }

        private void addMessage(String message) {
            messages.add(message);
        }

        private Entry(String clusterName, String clusterType, String serviceType,
                     boolean ignoreForInternalRedeploy, boolean deferChanges) {
            this.clusterName = clusterName;
            this.clusterType = clusterType;
            this.serviceType = serviceType;
            this.ignoreForInternalRedeploy = ignoreForInternalRedeploy;
            this.deferChanges = deferChanges;
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

        public boolean ignoreForInternalRedeploy() {
            return ignoreForInternalRedeploy;
        }

        public boolean deferChanges() {
            return deferChanges;
        }

        public Set<ServiceInfo> getServices() {
            return services;
        }

        public Set<String> getMessages() {
            return messages;
        }

    }

    private final Map<String, Entry> actions = new TreeMap<>();

    public RestartActions() { }

    private RestartActions(Map<String, Entry> actions) {
        this.actions.putAll(actions);
    }

    public RestartActions(List<ConfigChangeAction> actions) {
        for (ConfigChangeAction action : actions) {
            if (action.getType().equals(ConfigChangeAction.Type.RESTART)) {
                boolean deferChanges = (action instanceof ConfigChangeRestartAction vra)
                        && vra.configChange() == ConfigChangeRestartAction.ConfigChange.DEFER_UNTIL_RESTART;
                for (ServiceInfo service : action.getServices()) {
                    addEntry(service, action.ignoreForInternalRedeploy(), deferChanges).
                            addService(service).
                            addMessage(action.getMessage());
                }
            }
        }
    }

    private Entry addEntry(ServiceInfo service, boolean ignoreForInternalRedeploy, boolean deferChanges) {
        String clusterName = service.getProperty("clustername").orElse("");
        String clusterType = service.getProperty("clustertype").orElse("");
        String entryId = clusterType + "." + clusterName + "." + service.getServiceType()
                       + "." + ignoreForInternalRedeploy + "." + deferChanges;
        Entry entry = actions.get(entryId);
        if (entry == null) {
            entry = new Entry(clusterName, clusterType, service.getServiceType(),
                            ignoreForInternalRedeploy, deferChanges);
            actions.put(entryId, entry);
        }
        return entry;
    }

    public RestartActions useForInternalRestart(boolean useForInternalRestart) {
        return new RestartActions(actions.entrySet().stream()
                .filter(entry -> !useForInternalRestart || !entry.getValue().ignoreForInternalRedeploy())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
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

    public Set<String> hostnames() {
        return getEntries().stream()
                           .flatMap(entry -> entry.getServices().stream())
                           .map(ServiceInfo::getHostName)
                           .collect(Collectors.toUnmodifiableSet());
    }

}
