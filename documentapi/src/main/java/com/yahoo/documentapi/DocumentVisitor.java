// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

/**
 * Visitor that simply returns documents found in storage.
 *
 * @author <a href="mailto:humbe@yahoo-inc.com">H&aring;kon Humberset</a>
 */
public class DocumentVisitor extends VisitorParameters {

    /**
     * Create a document visitor.
     *
     * @param documentSelection The document selection criteria.
     */
    public DocumentVisitor(String documentSelection) {
        super(documentSelection);
    }

    // Inherited docs from VisitorParameters
    public String getVisitorLibrary() { return "DumpVisitor"; }

}
