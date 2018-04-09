// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Einar M R Rosenvinge
 */
final public class Document {

    private final String documentId;
    private final ByteBuffer data;
    private final long createTimeMillis = System.currentTimeMillis();
    // This is initialized lazily to reduce work on calling thread (which is the thread calling the API).
    private String operationId = null;
    private final Object context;
    private long queueInsertTimestampMillis;

    public Document(String documentId, byte[] data, Object context) {
        this.documentId = documentId;
        this.context = context;
        this.data = ByteBuffer.wrap(data);
    }

    public Document(String documentId, CharSequence data, Object context) {
        this.documentId = documentId;
        this.context = context;
        try {
            this.data = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(data));
        } catch (CharacterCodingException e) {
            throw new RuntimeException("Error encoding document data into UTF8 " + documentId, e);
        }
    }

    public void resetQueueTime() {
        queueInsertTimestampMillis = System.currentTimeMillis();
    }

    public long timeInQueueMillis() {
        return System.currentTimeMillis() - queueInsertTimestampMillis;
    }

    public CharSequence getDataAsString() {
        return StandardCharsets.UTF_8.decode(data.asReadOnlyBuffer());
    }

    public Object getContext() {
        return context;
    }

    public static class DocumentException extends IOException {
        private static final long serialVersionUID = 29832833292L;
        public DocumentException(String message)
        {
            super(message);
        }
    }

    public String getDocumentId() {
        return documentId;
    }

    public ByteBuffer getData() {
        return data.asReadOnlyBuffer();
    }

    public int size() {
        return data.remaining();
    }

    public long createTimeMillis() {
        return createTimeMillis;
    }

    public String getOperationId() {
        if (operationId == null) {
            operationId = new BigInteger(64, ThreadLocalRandom.current()).toString(32);
        }
        return operationId;
    }

}
