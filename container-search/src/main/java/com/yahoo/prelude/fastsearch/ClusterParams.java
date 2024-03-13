// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.search.schema.SchemaInfo;

/**
 * Helper class for carrying around cluster-related
 * config parameters to the VespaBackend class.
 *
 * @author arnej27959
 */
public class ClusterParams {

    private final String searcherName;
    private final String serverId;
    private final String defaultSummary;
    private final DocumentdbInfoConfig documentdbInfoConfig;
    private final SchemaInfo schemaInfo;

    public ClusterParams(String name) {
        this(name, "server.0", null, null, null);
    }
    public ClusterParams(String name, String serverId, String defaultSummary,
                         DocumentdbInfoConfig documentdbInfoConfig, SchemaInfo schemaInfo) {
        this.searcherName = name;
        this.serverId = serverId;
        if (defaultSummary != null && defaultSummary.isEmpty())
            this.defaultSummary = null;
        else
            this.defaultSummary = defaultSummary;
        this.documentdbInfoConfig = documentdbInfoConfig;
        this.schemaInfo = schemaInfo;
    }

    public String getServerId() { return serverId; }
    public String getSearcherName() { return searcherName; }
    public String getDefaultSummary() { return defaultSummary; }
    public DocumentdbInfoConfig getDocumentdbInfoConfig() { return documentdbInfoConfig; }
    public SchemaInfo getSchemaInfo() { return schemaInfo; }
}
