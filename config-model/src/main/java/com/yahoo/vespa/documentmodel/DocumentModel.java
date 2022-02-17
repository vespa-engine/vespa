// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

import com.yahoo.documentmodel.DocumentTypeRepo;

/**
 * DocumentModel represents everything derived from a set of search definitions.
 * It contains a document manager managing all defined document types.
 * It contains a search manager managing all specified search definitions.
 * It contains a storage manager managing all specified storage definitions.
 *
 * @author baldersheim
 */
public class DocumentModel {

    private final DocumentTypeRepo documentMan = new DocumentTypeRepo();
    private final SearchManager searchMan = new SearchManager();

    public DocumentTypeRepo getDocumentManager() { return documentMan; }

    public SearchManager getSearchManager() { return searchMan; }

}
