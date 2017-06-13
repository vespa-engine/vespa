// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib;

import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.vespa.objects.Serializer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class DocumentList {

    protected DocumentList() { }

    /**
     * Creates a DocumentList from serialized form.
     *
     * @param docMan Documentmanager to use when deserializing
     * @param buffer the buffer to read from
     * @return a DocumentList instance
     */
    public static DocumentList create(DocumentTypeManager docMan, byte[] buffer) {
        return new BinaryDocumentList(docMan, buffer);
    }

    /**
     * Creates a DocumentList from a list of entries.
     * @param entries the entries to create a DocumentList from
     * @return a DocumentList instance
     * @see com.yahoo.vdslib.Entry
     */
    public static DocumentList create(List<Entry> entries) {
        return new DynamicDocumentList(entries);
    }

    /**
     * Creates a DocumentList containing a single entry.
     *
     * @param entry the entry to create a DocumentList from
     * @return a DocumentList instance
     * @see com.yahoo.vdslib.Entry
     */
    public static DocumentList create(Entry entry) {
        return new DynamicDocumentList(entry);
    }

    /**
     * Retrieves the specified Entry from the list.
     *
     * @param index the index of the Entry to return (0-based)
     * @return the entry at the specified position
     * @throws ArrayIndexOutOfBoundsException if index is &lt; 0 or &gt; size()
     * @throws com.yahoo.document.serialization.DeserializationException if the DocumentList is stored in binary form internally and deserialization fails
     */
    public abstract Entry get(int index) throws ArrayIndexOutOfBoundsException;

    /**
     * Returns the size of the list.
     *
     * @return the size of the list
     */
    public abstract int size();

    /**
     * Returns the byte size of the list. The value returned is exact if the list is stored in
     * binary form internally, otherwise it is approximate.
     *
     * @return the byte size of the list
     */
    public abstract int getApproxByteSize();

    /**
     * Serialize the list into the given buffer.
     *
     * @param buf the buffer to serialize into
     */
    public abstract void serialize(Serializer buf);

    /**
     * Test if a contains b
     *
     * @param list DocumentList contained
     * @return true if a contains b
     */
    public boolean containsAll(DocumentList list) {
        if( this.size() < list.size()) {
            return false;
        }

        Map<DocumentId, Integer> indexes = new HashMap<DocumentId, Integer>();
        for (int i=0; i<this.size(); ++i) {
            indexes.put(this.get(i).getDocumentOperation().getId(), i);
        }
        for (int i=0; i<list.size(); ++i) {
            Integer index = indexes.get(list.get(i).getDocumentOperation().getId());
            if (index == null ||
                    list.get(i).getTimestamp() != this.get(index).getTimestamp() ||
                    list.get(i).kind() != this.get(index).kind())
            {
                return false;
            }
        }
        return true;
    }

}

