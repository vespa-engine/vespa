// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

import com.yahoo.documentmodel.DocumentTypeRepo;

/**
 * DocumentModel represents everything derived from a set of search definitions.
 * It contains a document manager managing all defined document types.
 * It contains a search manager managing all specified search definitions.
 * It contains a storage manager managing all specified storage definitions.
 *
 * @author    baldersheim
 * @since     2010-02-19
 */
public class DocumentModel {
    private DocumentTypeRepo documentMan = new DocumentTypeRepo();
    private SearchManager         searchMan = new SearchManager();

    /**
     *
     * @return Returns the DocumentManager
     */
    public DocumentTypeRepo getDocumentManager() { return documentMan; }

    /**
     *
     * @return Returns the SearchManager
     */
    public SearchManager     getSearchManager() { return searchMan; }

}
