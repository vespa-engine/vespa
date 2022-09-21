// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.Document;
import com.yahoo.document.serialization.DocumentDeserializer;

import java.nio.ByteBuffer;

/**
 * @author Simon Thoresen Hult
 */
public class GetDocumentReply extends DocumentAcceptedReply {

    private DocumentDeserializer buffer = null;
    private Document document = null;
    private long lastModified = 0;
    private LazyDecoder decoder = null;

    /**
     * Constructs a new reply for deserialization.
     */
    GetDocumentReply() {
        super(DocumentProtocol.REPLY_GETDOCUMENT);
    }

    /**
     * Constructs a new reply to lazily deserialize from a byte buffer.
     *
     * @param decoder The decoder to use for deserialization.
     * @param buf     A byte buffer that contains a serialized reply.
     */
    GetDocumentReply(LazyDecoder decoder, DocumentDeserializer buf) {
        super(DocumentProtocol.REPLY_GETDOCUMENT);
        this.decoder = decoder;
        buffer = buf;
    }

    /**
     * Constructs a new document get reply.
     *
     * @param doc The document requested.
     */
    public GetDocumentReply(Document doc) {
        super(DocumentProtocol.REPLY_GETDOCUMENT);
        document = doc;
    }

    /**
     * This method will make sure that any serialized content is deserialized into proper message content on first
     * entry. Any subsequent entry into this function will do nothing.
     */
    private void deserialize() {
        if (decoder != null && buffer != null) {
            decoder.decode(this, buffer);
            decoder = null;
            buffer = null;
        }
    }

    /**
     * Returns the document retrieved.
     *
     * @return The document.
     */
    public Document getDocument() {
        deserialize();
        return document;
    }

    /**
     * Sets the document of this reply.
     *
     * @param doc The document to set.
     */
    public void setDocument(Document doc) {
        buffer = null;
        decoder = null;
        document = doc;
        lastModified = document != null && document.getLastModified() != null ? document.getLastModified() : 0;
    }

    /**
     * Returns the date the document was last modified.
     *
     * @return The date.
     */
    public long getLastModified() {
        deserialize();
        return lastModified;
    }

    /**
     * Set the date the document was last modified.
     *
     * @param modified The date.
     */
    void setLastModified(long modified) {
        lastModified = modified;
    }

    /**
     * Returns the internal buffer to deserialize from, may be null.
     *
     * @return The buffer.
     */
    public ByteBuffer getSerializedBuffer() {
        return buffer != null ? buffer.getBuf().getByteBuffer() : null;
    }
}
