// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.document.SDDocumentType;

/**
 * A search that was derived from an sd file containing no search element(s), only
 * document specifications, so the name of this is decided by parsing and adding the document instance.
 *
 * @author vegardh
 */
public class DocumentOnlySearch extends Search {

    public DocumentOnlySearch() {
        // empty
    }

    @Override
    public void addDocument(SDDocumentType docType) {
        if (getName() == null) {
            setName(docType.getName());
        }
        super.addDocument(docType);
    }

}
