// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

/**
 * Visitor that simply returns documents found in storage.
 *
 * @author Håkon Humberset
 */
public class DocumentVisitor extends VisitorParameters {

    /**
     * Creates a document visitor.
     *
     * @param documentSelection the document selection criteria.
     */
    public DocumentVisitor(String documentSelection) {
        super(documentSelection);
    }

    public String getVisitorLibrary() { return "DumpVisitor"; }

}
