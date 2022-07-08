package com.yahoo.vespa.orchestrator.model;

public enum ContentService {
    DISTRIBUTOR("distributor"), STORAGE_NODE("storage");

    private final String nameInClusterController;

    ContentService(String nameInClusterController) {
        this.nameInClusterController = nameInClusterController;
    }

    public String nameInClusterController() { return nameInClusterController; }
}
