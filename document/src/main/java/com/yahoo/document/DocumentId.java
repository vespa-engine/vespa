// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.idstring.IdString;
import com.yahoo.document.serialization.DeserializationException;
import com.yahoo.document.serialization.DocumentReader;
import com.yahoo.document.serialization.DocumentWriter;
import com.yahoo.document.serialization.SerializationException;
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
     * Creates a document id based on the given document id URI string.
     *
     * The document id string can only contain text characters.
     */
    public DocumentId(String id) {
        if (id == null) {
            throw new IllegalArgumentException("Cannot create DocumentId from null id.");
        }
        if (id.length() > IdString.MAX_LENGTH) {
            throw new IllegalArgumentException("The document id(" + id.length() + ") is too long(" + IdString.MAX_LENGTH + "). " +
                    "However if you have already fed a document earlier on and want to remove it, you can do so by " +
                    "calling new DocumentId(IdString.createIdStringLessStrict()) that will bypass this restriction.");
        }
        this.id = IdString.createIdString(id);
        globalId = null;
    }

    public DocumentId(IdString id) {
        this.id = id;
        globalId = null;
    }

    /**
     * Creates a document id based on a serialized document id URI string.
     *
     * The document id string is not allowed to contain 0x0 byte characters.
     * Otherwise all characters are allowed to ensure that document ids
     * already stored can be de-serialized.
     */
    public static DocumentId createFromSerialized(String id) {
        return new DocumentId(IdString.createFromSerialized(id));
    }

    @Override
    public DocumentId clone() {
        return  (DocumentId)super.clone();
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
            id = IdString.createFromSerialized(data.getString(null));
        }
    }

    public boolean hasDocType() {
        return id.hasDocType();
    }

    public String getDocType() {
        return id.getDocType();
    }
}
