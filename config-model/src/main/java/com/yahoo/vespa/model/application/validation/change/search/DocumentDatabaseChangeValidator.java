// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change.search;

import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.vespa.model.application.validation.ValidationOverrides;
import com.yahoo.vespa.model.application.validation.change.VespaConfigChangeAction;
import com.yahoo.vespa.model.search.DocumentDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates the changes between a current and next document database that is part of an indexed search cluster.
 *
 * @author geirst
 * @since 2014-11-18
 */
public class DocumentDatabaseChangeValidator {

    private DocumentDatabase currentDatabase;
    private NewDocumentType currentDocType;
    private DocumentDatabase nextDatabase;
    private NewDocumentType nextDocType;

    public DocumentDatabaseChangeValidator(DocumentDatabase currentDatabase,
                                           NewDocumentType currentDocType,
                                           DocumentDatabase nextDatabase,
                                           NewDocumentType nextDocType) {
        this.currentDatabase = currentDatabase;
        this.currentDocType = currentDocType;
        this.nextDatabase = nextDatabase;
        this.nextDocType = nextDocType;
    }

    public List<VespaConfigChangeAction> validate(final ValidationOverrides overrides) {
        List<VespaConfigChangeAction> result = new ArrayList<>();
        result.addAll(validateAttributeChanges(overrides));
        result.addAll(validateIndexingScriptChanges(overrides));
        result.addAll(validateDocumentTypeChanges(overrides));
        return result;
    }

    private List<VespaConfigChangeAction> validateAttributeChanges(final ValidationOverrides overrides) {
        return new AttributeChangeValidator(
                currentDatabase.getDerivedConfiguration().getAttributeFields(),
                currentDatabase.getDerivedConfiguration().getIndexSchema(), currentDocType,
                nextDatabase.getDerivedConfiguration().getAttributeFields(),
                nextDatabase.getDerivedConfiguration().getIndexSchema(), nextDocType).validate(overrides);
    }

    private List<VespaConfigChangeAction> validateIndexingScriptChanges(ValidationOverrides overrides) {
        return new IndexingScriptChangeValidator(currentDatabase.getDerivedConfiguration().getSearch(),
                nextDatabase.getDerivedConfiguration().getSearch()).validate(overrides);
    }

    private List<VespaConfigChangeAction> validateDocumentTypeChanges(ValidationOverrides overrides) {
        return new DocumentTypeChangeValidator(currentDocType, nextDocType).validate(overrides);
    }

}
