// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.api.annotations.Beta;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.serialization.DocumentDeserializer;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author Simon Thoresen Hult
 */
public class PutDocumentMessage extends TestAndSetMessage {

    private DocumentDeserializer buffer = null;
    private DocumentPut put = null;
    private long time = 0;
    private LazyDecoder decoder = null;

    /**
     * Constructs a new message from a byte buffer.
     *
     * @param decoder The decoder to use for deserialization.
     * @param buffer  A byte buffer that contains a serialized message.
     */
    public PutDocumentMessage(LazyDecoder decoder, DocumentDeserializer buffer) {
        this.decoder = decoder;
        this.buffer = buffer;
    }

    /** Constructs a new document put message */
    public PutDocumentMessage(DocumentPut put) {
        this.put = put;
    }

    /**
     * Creates an empty PutDocumentMessage
     */
    public static PutDocumentMessage createEmpty() {
        return new PutDocumentMessage(null);
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

    /** Returns the document put operation */
    public DocumentPut getDocumentPut() {
        deserialize();
        return put;
    }

    /** Sets the document to put */
    public void setDocumentPut(DocumentPut put) {
        buffer = null;
        decoder = null;
        this.put = put;
    }

    /** Returns the timestamp of the document to put */
    public long getTimestamp() {
        deserialize();
        return time;
    }

    /** Sets the timestamp of the document to put */
    public void setTimestamp(long time) {
        buffer = null;
        decoder = null;
        this.time = time;
    }

    /**
     * Returns the raw serialized buffer. This buffer is stored as the message is received from accross the network, and
     * deserialized from as soon as a member is requested. This method will return null if the buffer has been decoded.
     *
     * @return the buffer containing the serialized data for this message, or null
     */
    ByteBuffer getSerializedBuffer() {
        return buffer != null ? buffer.getBuf().getByteBuffer() : null; // TODO: very dirty. Must make interface.
    }

    @Override
    public DocumentReply createReply() {
        return new WriteDocumentReply(DocumentProtocol.REPLY_PUTDOCUMENT);
    }

    @Override
    public int getApproxSize() {
        if (buffer != null) {
            return buffer.getBuf().remaining();
        }
        return put.getDocument().getApproxSize();
    }

    @Override
    public boolean hasSequenceId() {
        return true;
    }

    @Override
    public long getSequenceId() {
        deserialize();
        return Arrays.hashCode(put.getId().getGlobalId());
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_PUTDOCUMENT;
    }

    @Override
    public TestAndSetCondition getCondition() {
        deserialize();
        return put.getCondition();
    }

    @Override
    public void setCondition(TestAndSetCondition condition) {
        put.setCondition(condition);
    }

    @Beta
    public void setCreateIfNonExistent(boolean value) {
        put.setCreateIfNonExistent(value);
    }

    @Beta
    public boolean getCreateIfNonExistent() {
        deserialize();
        return put.getCreateIfNonExistent();
    }
}
