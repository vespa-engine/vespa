// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

/**
 * Superclass of the classes which contains the parameters for creating or opening a document access.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class DocumentAccessParams {

    // The id to resolve to document manager config.
    private String documentManagerConfigId = "client";

    /**
     * Returns the config id that the document manager should subscribe to.
     *
     * @return The config id.
     */
    public String getDocumentManagerConfigId() {
        return documentManagerConfigId;
    }

    /**
     * Sets the config id that the document manager should subscribe to.
     *
     * @param configId The config id.
     * @return This, to allow chaining.
     */
    public DocumentAccessParams setDocumentManagerConfigId(String configId) {
        documentManagerConfigId = configId;
        return this;
    }
}