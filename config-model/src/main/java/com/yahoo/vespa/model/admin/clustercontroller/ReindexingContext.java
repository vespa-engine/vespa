// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.clustercontroller;

import com.yahoo.config.model.api.Reindexing;
import com.yahoo.documentmodel.NewDocumentType;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * Context required to configure automatic reindexing for a given cluster controller cluster (for a given content cluster).
 *
 * @author bjorncs
 */
public class ReindexingContext {

    private final Reindexing reindexing;
    private final String contentClusterName;
    private final Collection<NewDocumentType> documentTypes;

    public ReindexingContext(
            Reindexing reindexing,
            String contentClusterName,
            Collection<NewDocumentType> documentTypes) {
        this.reindexing = reindexing;
        this.contentClusterName = Objects.requireNonNull(contentClusterName);
        this.documentTypes = Objects.requireNonNull(documentTypes);
    }

    public Optional<Reindexing> reindexing() { return Optional.ofNullable(reindexing); }
    public String contentClusterName() { return contentClusterName; }
    public Collection<NewDocumentType> documentTypes() { return documentTypes; }
}
