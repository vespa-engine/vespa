// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.config.DocumentmanagerConfig;

import java.util.Optional;

/**
 * Superclass of the classes which contains the parameters for creating or opening a document access.
 *
 * @author Simon Thoresen Hult
 */
public class DocumentAccessParams {

    /** The id to resolve to document manager config. Not needed if the config is passed here */
    private String documentManagerConfigId = "client";

    /** The document manager config, or empty if not provided (in which case a subscription must be created) */
    private Optional<DocumentmanagerConfig> documentmanagerConfig = Optional.empty();

    /** Returns the config id that the document manager should subscribe to. */
    public String getDocumentManagerConfigId() { return documentManagerConfigId; }

    /** Returns the document manager config to use, or empty if it it necessary to subscribe to get it */
    public Optional<DocumentmanagerConfig> documentmanagerConfig() { return documentmanagerConfig; }

    /** Sets the config id that the document manager should subscribe to. */
    public DocumentAccessParams setDocumentManagerConfigId(String configId) {
        documentManagerConfigId = configId;
        return this;
    }

    public DocumentAccessParams setDocumentmanagerConfig(DocumentmanagerConfig documentmanagerConfig) {
        this.documentmanagerConfig = Optional.of(documentmanagerConfig);
        return this;
    }

}
