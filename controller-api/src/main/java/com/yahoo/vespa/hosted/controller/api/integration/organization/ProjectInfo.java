// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import java.util.Map;

/**
 * @author jvenstad
 * @author mortent
 */
public class ProjectInfo {

    private final String id;
    private final Map<String, String> componentIds;

    public ProjectInfo(String id, Map<String, String> componentIds) {
        this.id = id;
        this.componentIds = componentIds;
    }

    public boolean hasComponent(String component) {
        return componentIds.containsKey(component);
    }

    public String id() {
        return id;
    }

    public Map<String, String> componentIds() {
        return componentIds;
    }
}