// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

import com.yahoo.documentmodel.DocumentTypeRepo;

/**
 * DocumentModel represents everything derived from a set of schemas.
 * It contains a document manager managing all defined document types, and
 * a search manager managing all search aspects of the schemas.
 *
 * @author baldersheim
 */
public class DocumentModel {

    private final DocumentTypeRepo documentManager = new DocumentTypeRepo();
    private final SearchManager searchManager = new SearchManager();

    public DocumentTypeRepo getDocumentManager() { return documentManager; }
    public SearchManager getSearchManager() { return searchManager; }

}
