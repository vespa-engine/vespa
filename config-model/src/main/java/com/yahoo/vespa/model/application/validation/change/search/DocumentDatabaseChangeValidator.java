// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change.search;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.vespa.model.application.validation.change.VespaConfigChangeAction;
import com.yahoo.vespa.model.search.DocumentDatabase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates the changes between a current and next document database that is part of an indexed search cluster.
 *
 * @author geirst
 */
public class DocumentDatabaseChangeValidator {

    private final ClusterSpec.Id id;
    private final DocumentDatabase currentDatabase;
    private final NewDocumentType currentDocType;
    private final DocumentDatabase nextDatabase;
    private final NewDocumentType nextDocType;

    public DocumentDatabaseChangeValidator(ClusterSpec.Id id,
                                           DocumentDatabase currentDatabase,
                                           NewDocumentType currentDocType,
                                           DocumentDatabase nextDatabase,
                                           NewDocumentType nextDocType) {
        this.id = id;
        this.currentDatabase = currentDatabase;
        this.currentDocType = currentDocType;
        this.nextDatabase = nextDatabase;
        this.nextDocType = nextDocType;
    }

    public List<VespaConfigChangeAction> validate() {
        List<VespaConfigChangeAction> result = new ArrayList<>();
        result.addAll(validateAttributeChanges());
        result.addAll(validateStructFieldAttributeChanges());
        result.addAll(validateIndexingScriptChanges());
        result.addAll(validateDocumentTypeChanges());
        return result;
    }

    private List<VespaConfigChangeAction> validateAttributeChanges() {
        return new AttributeChangeValidator(id,
                                            currentDatabase.getDerivedConfiguration().getAttributeFields(),
                                            currentDatabase.getDerivedConfiguration().getIndexSchema(), currentDocType,
                                            nextDatabase.getDerivedConfiguration().getAttributeFields(),
                                            nextDatabase.getDerivedConfiguration().getIndexSchema(), nextDocType)
                       .validate();
    }

    private List<VespaConfigChangeAction> validateStructFieldAttributeChanges() {
        return new StructFieldAttributeChangeValidator(id,
                                                       currentDocType,
                                                       currentDatabase.getDerivedConfiguration().getAttributeFields(),
                                                       nextDocType,
                                                       nextDatabase.getDerivedConfiguration().getAttributeFields())
                       .validate();
    }

    private List<VespaConfigChangeAction> validateIndexingScriptChanges() {
        return new IndexingScriptChangeValidator(id,
                                                 currentDatabase.getDerivedConfiguration().getSearch(),
                                                 nextDatabase.getDerivedConfiguration().getSearch())
                       .validate();
    }

    private List<VespaConfigChangeAction> validateDocumentTypeChanges() {
        return new DocumentTypeChangeValidator(id, currentDocType, nextDocType)
                       .validate();
    }

}
