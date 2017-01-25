// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import java.util.Iterator;
import java.util.Map;

/**
 * Contains all document references for a document mapped by field name
 *
 * @author bjorncs
 */
public class DocumentReferences implements Iterable<Map.Entry<String, DocumentReference>> {
    private final Map<String, DocumentReference> references;

    public DocumentReferences(Map<String, DocumentReference> references) {
        this.references = references;
    }

    @Override
    public Iterator<Map.Entry<String, DocumentReference>> iterator() {
        return references.entrySet().iterator();
    }

}
