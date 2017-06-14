// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.idstring.IdString;
import com.yahoo.document.serialization.*;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.Identifiable;
import com.yahoo.vespa.objects.Serializer;

import java.io.Serializable;

/**
 * The id of a document
 */
public class DocumentId extends Identifiable implements Serializable {

    private IdString id;
    private GlobalId globalId;

    /**
     * Constructor used for deserialization.
     */
    public DocumentId(Deserializer buf) {
        deserialize(buf);
    }

    /**
     * Constructor. This constructor is used if the DocumentId is used outside of a Document object, but we have the
     * URI.
     *
     * @param id Associate with this URI, storage address etc. is not applicable.
     */
    public DocumentId(String id) {
        if (id == null) {
            throw new IllegalArgumentException("Cannot create DocumentId from null id.");
        }
        this.id = IdString.createIdString(id);
        globalId = null;
    }

    public DocumentId(IdString id) {
        this.id = id;
        globalId = null;
    }

    @Override
    public DocumentId clone() {
        DocumentId docId =  (DocumentId)super.clone();
        return docId;
    }

    public void setId(IdString id) {
        this.id = id;
    }

    public IdString getScheme() {
        return id;
    }

    public byte[] getGlobalId() {
        if (globalId == null) {
            globalId = new GlobalId(id);
        }
        return globalId.getRawId();
    }

    public int compareTo(Object o) {
        DocumentId cmp = (DocumentId)o;
        return id.toString().compareTo(cmp.id.toString());
    }

    public boolean equals(Object o) {
        return o instanceof DocumentId && id.equals(((DocumentId)o).id);
    }

    public int hashCode() {
        return id.hashCode();
    }

    public String toString() {
        return id.toString();
    }

    @Override
    public void onSerialize(Serializer target) throws SerializationException {
        if (target instanceof DocumentWriter) {
            ((DocumentWriter)target).write(this);
        } else {
            target.put(null, id.toString());
        }
    }


    public void onDeserialize(Deserializer data) throws DeserializationException {
        if (data instanceof DocumentReader) {
            id = ((DocumentReader)data).readDocumentId().getScheme();
        } else {
            id = IdString.createIdString(data.getString(null));
        }
    }

    public boolean hasDocType() {
        return id.hasDocType();
    }

    public String getDocType() {
        return id.getDocType();
    }
}
