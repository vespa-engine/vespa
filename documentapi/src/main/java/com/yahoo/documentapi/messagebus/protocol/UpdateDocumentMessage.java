// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.serialization.DocumentDeserializer;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author Simon Thoresen Hult
 */
public class UpdateDocumentMessage extends TestAndSetMessage {

    private DocumentDeserializer buffer = null;
    private DocumentUpdate update = null;
    private long oldTime = 0;
    private long newTime = 0;
    private LazyDecoder decoder = null;

    /**
     * Constructs a new message from a byte buffer.
     *  @param decoder The decoder to use for deserialization.
     * @param buffer  A byte buffer that contains a serialized message.
     */
    public UpdateDocumentMessage(LazyDecoder decoder, DocumentDeserializer buffer) {
        this.decoder = decoder;
        this.buffer = buffer;
    }

    /**
     * Constructs a new document update message.
     *
     * @param upd The document update to perform.
     */
    public UpdateDocumentMessage(DocumentUpdate upd) {
        super();
        update = upd;
    }

    /**
     * Creates an empty UpdateDocumentMessage
     */
    public static UpdateDocumentMessage createEmpty() {
        return new UpdateDocumentMessage(null);
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
     * Returns the document update to perform.
     *
     * @return The update.
     */
    public DocumentUpdate getDocumentUpdate() {
        deserialize();
        return update;
    }

    /**
     * Sets the document update to perform.
     *
     * @param upd The document update to set.
     */
    public void setDocumentUpdate(DocumentUpdate upd) {
        if (upd == null) {
            throw new IllegalArgumentException("Document update can not be null.");
        }
        buffer = null;
        decoder = null;
        update = upd;
    }

    /**
     * Returns the timestamp required for this update to be applied.
     *
     * @return The document timestamp.
     */
    public long getOldTimestamp() {
        deserialize();
        return oldTime;
    }

    /**
     * Sets the timestamp required for this update to be applied.
     *
     * @param time The timestamp to set.
     */
    public void setOldTimestamp(long time) {
        buffer = null;
        decoder = null;
        oldTime = time;
    }

    /**
     * Returns the timestamp to assign to the updated document.
     *
     * @return The document timestamp.
     */
    public long getNewTimestamp() {
        deserialize();
        return newTime;
    }

    /**
     * Sets the timestamp to assign to the updated document.
     *
     * @param time The timestamp to set.
     */
    public void setNewTimestamp(long time) {
        buffer = null;
        decoder = null;
        newTime = time;
    }

    /**
     * Returns the raw serialized buffer. This buffer is stored as the message is received from accross the network, and
     * deserialized from as soon as a member is requested. This method will return null if the buffer has been decoded.
     *
     * @return The buffer containing the serialized data for this message, or null.
     */
    ByteBuffer getSerializedBuffer() {
        return buffer != null ? buffer.getBuf().getByteBuffer() : null;
    }

    @Override
    public DocumentReply createReply() {
        return new UpdateDocumentReply();
    }

    @Override
    public boolean hasSequenceId() {
        return true;
    }

    @Override
    public long getSequenceId() {
        deserialize();
        return Arrays.hashCode(update.getId().getGlobalId());
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_UPDATEDOCUMENT;
    }

    @Override
    public TestAndSetCondition getCondition() {
        deserialize();
        return update.getCondition();
    }

    @Override
    public void setCondition(TestAndSetCondition condition) {
        this.update.setCondition(condition);
    }
}
