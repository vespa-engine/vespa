// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib;

import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;

/**
 * Represents a document operation in a DocumentList, which can currently be
 * PUT, REMOVE and UPDATE.
 *
 * @author <a href="mailto:thomasg@yahoo-inc.com">Thomas Gundersen</a>, <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public abstract class Entry {

    protected Entry() { }

    /**
     * Creates a new entry from serialized form.
     *
     * @param docMan Documentmanager to use when deserializing
     * @param buffer the buffer to read the entry from
     * @param entryIndex the index of the entry in the buffer
     * @return an Entry reading from the buffer
     */
    public static Entry create(DocumentTypeManager docMan, byte[] buffer, int entryIndex) {
        return new BinaryEntry(docMan, buffer, entryIndex);
    }

    /**
     * Creates a new entry from a document operation.
     *
     * @param op the document in the entry
     * @param bodyStripped true if the document contains only header fields
     * @return an Entry for this document
     */
    public static Entry create(DocumentOperation op, boolean bodyStripped) {
        return new DynamicEntry(op, bodyStripped);
    }

    /**
     * Creates a new entry from a document operation.
     *
     * @param op the document in the entry
     * @return an Entry for this document
     */
    public static Entry create(DocumentOperation op) {
        return create(op, false);
    }
    /**
     * Creates a new entry from a document remove operation.
     *
     * @param doc the document in the entry
     * @return an Entry for this document
     */
    public static Entry create(DocumentRemove doc) {
        return new DynamicEntry(doc);
    }

    /**
     * Creates a new entry from a document update operation.
     *
     * @param doc the document update in the entry
     * @return an Entry for this document update
     */
    public static Entry create(DocumentUpdate doc) {
        return new DynamicEntry(doc);
    }

    /**
     * Entries in iterators gotten from DocumentList::end() are invalid.
     * @return true if valid
     */
    public abstract boolean valid();

    /**
     * Returns true if the Document represented by this entry has been removed from persistent storage.
     *
     * @return true if the Document has been removed
     */
    public abstract boolean isRemoveEntry();

    /**
     * Returns true if the Document represented by this entry has gotten its body fields stripped
     * away (note: the body fields might still be stored in persistent storage).
     *
     * @return true if the Document only has header fields
     */
    public abstract boolean isBodyStripped();

    /**
     * Returns true if this entry represents a document update operation.
     *
     * @return true if this is a document update operation
     */
    public abstract boolean isUpdateEntry();


    public int kind(){
        if (isRemoveEntry()) {
            return 0; //REMOVE
        }
        if (isUpdateEntry()) {
            return 2; //UPDATE
        }
        return 1; // PUT
    }

    /**
     * Returns the timestamp (last modified) of this entry, from persistent storage.
     * @return the last modified timestamp of this entry
     */
    public abstract long getTimestamp();

    /**
     * Returns the DocumentPut or DocumentUpdate operation in this entry.
     *
     * @return the DocumentOperation in this entry.
     */
    public abstract DocumentOperation getDocumentOperation();

    /**
     * Returns the Document header (if this is a DocumentPut or a DocumentRemove operation), otherwise
     * a DocumentUpdate operation.
     *
     * @return a DocumentPut operation containing a Document with only the header fields present
     * @throws RuntimeException if deserialization fails, or if this is a DocumentUpdate operation
     */
    public abstract DocumentOperation getHeader();

    @Override
    public boolean equals(Object obj) {
        if ( this == obj ) {
            return true;
        }
        if (!(obj instanceof Entry)) {
            return false;
        }
        Entry entry = (Entry) obj;
        return this.getDocumentOperation().getId().equals(entry.getDocumentOperation().getId()) &&
                this.getTimestamp() == entry.getTimestamp() &&
                this.kind() == entry.kind() &&
                this.isBodyStripped() == entry.isBodyStripped() &&
                this.valid() == entry.valid();
    }

    @Override
    public int hashCode() {
        int res = 31;
        res = 31 * res + getDocumentOperation().getId().hashCode();
        res = (int) (31 * res + getTimestamp());
        res = 31 * res + kind()*31;
        res = 31 * res + (isBodyStripped() ? 17 : 249);
        res = 31 * res + (valid() ? 333 : 31);

        return res;
    }
}
