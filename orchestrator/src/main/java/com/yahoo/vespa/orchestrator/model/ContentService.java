// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

public enum ContentService {
    DISTRIBUTOR("distributor"), STORAGE_NODE("storage");

    private final String nameInClusterController;

    ContentService(String nameInClusterController) {
        this.nameInClusterController = nameInClusterController;
    }

    public String nameInClusterController() { return nameInClusterController; }
}
