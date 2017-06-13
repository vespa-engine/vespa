// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.document.SDDocumentType;

/**
 * A search that was derived from an sd file containing no search element(s), only
 * document specifications.
 *
 * @author vegardh
 *
 */
 // Award for best class name goes to ...
public class UnproperSearch extends Search {
    // This class exists because the parser accepts SD files without search { ... , and
    // there are unit tests using it too, BUT there are many nullpointer bugs if you try to
    // deploy such a file. Using this class to try to catch those.
    // TODO: Throw away this when we properly support doc-only SD files.

    public UnproperSearch() {
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
