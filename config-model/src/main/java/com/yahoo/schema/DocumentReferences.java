// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

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

    public void mergeFrom(DocumentReferences other) {
        references.putAll(other.references);
    }

    @Override
    public Iterator<Map.Entry<String, DocumentReference>> iterator() {
        return Collections.unmodifiableSet(references.entrySet()).iterator();
    }

    public Map<String, DocumentReference> referenceMap() {
        return Collections.unmodifiableMap(references);
    }

    public Stream<Map.Entry<String, DocumentReference>> stream() {
        return references.entrySet().stream();
    }
}
