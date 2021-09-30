// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.slime.Cursor;

import java.util.Set;

/**
 * Class used to convert a ConfigChangeActions instance to Slime.
 *
 * @author geirst
 */
public class ConfigChangeActionsSlimeConverter {
    private final ConfigChangeActions actions;

    public ConfigChangeActionsSlimeConverter(ConfigChangeActions actions) {
        this.actions = actions;
    }

    public void toSlime(Cursor root) {
        Cursor actionsCursor = root.setObject("configChangeActions");
        restartActionsToSlime(actionsCursor);
        refeedActionsToSlime(actionsCursor);
        reindexActionsToSlime(actionsCursor);
    }

    private void restartActionsToSlime(Cursor actionsCursor) {
        Cursor restartCursor = actionsCursor.setArray("restart");
        for (RestartActions.Entry entry : actions.getRestartActions().getEntries()) {
            Cursor entryCursor = restartCursor.addObject();
            entryCursor.setString("clusterName", entry.getClusterName());
            entryCursor.setString("clusterType", entry.getClusterType());
            entryCursor.setString("serviceType", entry.getServiceType());
            messagesToSlime(entryCursor, entry.getMessages());
            servicesToSlime(entryCursor, entry.getServices());
        }
    }

    private void refeedActionsToSlime(Cursor actionsCursor) {
        Cursor refeedCursor = actionsCursor.setArray("refeed");
        for (RefeedActions.Entry entry : actions.getRefeedActions().getEntries()) {
            Cursor entryCursor = refeedCursor.addObject();
            entryCursor.setString("name", entry.name());
            entryCursor.setString("documentType", entry.getDocumentType());
            entryCursor.setString("clusterName", entry.getClusterName());
            messagesToSlime(entryCursor, entry.getMessages());
            servicesToSlime(entryCursor, entry.getServices());
        }
    }

    private void reindexActionsToSlime(Cursor actionsCursor) {
        Cursor refeedCursor = actionsCursor.setArray("reindex");
        for (ReindexActions.Entry entry : actions.getReindexActions().getEntries()) {
            Cursor entryCursor = refeedCursor.addObject();
            entryCursor.setString("name", entry.name());
            entryCursor.setString("documentType", entry.getDocumentType());
            entryCursor.setString("clusterName", entry.getClusterName());
            messagesToSlime(entryCursor, entry.getMessages());
            servicesToSlime(entryCursor, entry.getServices());
        }
    }

    private static void messagesToSlime(Cursor entryCursor, Set<String> messages) {
        Cursor messagesCursor = entryCursor.setArray("messages");
        for (String message : messages) {
            messagesCursor.addString(message);
        }
    }

    private static void servicesToSlime(Cursor entryCursor, Set<ServiceInfo> services) {
        Cursor servicesCursor = entryCursor.setArray("services");
        for (ServiceInfo service : services) {
            Cursor serviceCursor = servicesCursor.addObject();
            serviceCursor.setString("serviceName", service.getServiceName());
            serviceCursor.setString("serviceType", service.getServiceType());
            serviceCursor.setString("configId", service.getConfigId());
            serviceCursor.setString("hostName", service.getHostName());
        }
    }
}
