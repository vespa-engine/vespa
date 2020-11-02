// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ConfigChangeReindexAction;
import com.yahoo.config.model.api.ServiceInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * @author bjorncs
 */
public class ReindexActions {

    private final Map<String, ReindexActions.Entry> actions = new TreeMap<>();

    public ReindexActions() {}

    public ReindexActions(List<ConfigChangeAction> actions) {
        for (ConfigChangeAction action : actions) {
            if (action.getType().equals(ConfigChangeAction.Type.REINDEX)) {
                ConfigChangeReindexAction reindexChange = (ConfigChangeReindexAction) action;
                for (ServiceInfo service : reindexChange.getServices()) {
                    addEntry(reindexChange.name(), reindexChange.allowed(), reindexChange.getDocumentType().orElse(null), service).
                            addService(service).
                            addMessage(action.getMessage());
                }
            }
        }
    }

    private Entry addEntry(String name, boolean allowed, String documentType, ServiceInfo service) {
        String clusterName = service.getProperty("clustername").orElse("");
        String entryId = name + "." + allowed + "." + clusterName + "." + documentType;
        Entry entry = actions.get(entryId);
        if (entry == null) {
            entry = new Entry(name, allowed, documentType, clusterName);
            actions.put(entryId, entry);
        }
        return entry;
    }

    public List<Entry> getEntries() { return new ArrayList<>(actions.values()); }
    public String format() { return new ReindexActionsFormatter(this).format(); }
    public boolean isEmpty() { return getEntries().isEmpty(); }

    public static class Entry {

        private final String name;
        private final boolean allowed;
        private final String documentType;
        private final String clusterName;
        private final Set<ServiceInfo> services = new LinkedHashSet<>();
        private final Set<String> messages = new TreeSet<>();

        private Entry(String name, boolean allowed, String documentType, String clusterName) {
            this.name = name;
            this.allowed = allowed;
            this.documentType = documentType;
            this.clusterName = clusterName;
        }

        private Entry addService(ServiceInfo service) {
            services.add(service);
            return this;
        }

        private Entry addMessage(String message) {
            messages.add(message);
            return this;
        }

        public String name() { return name; }
        public boolean allowed() { return allowed; }
        public String getDocumentType() { return documentType; }
        public String getClusterName() { return clusterName; }
        public Set<ServiceInfo> getServices() { return services; }
        public Set<String> getMessages() { return messages; }

    }
}
