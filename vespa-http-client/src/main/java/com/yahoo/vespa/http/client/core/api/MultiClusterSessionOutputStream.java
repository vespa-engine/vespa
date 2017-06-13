// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.api;

import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.operationProcessor.OperationProcessor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Class for wiring up the Session API. It is the return value of stream() in the Session API.
 * @author dybis
*/
class MultiClusterSessionOutputStream extends ByteArrayOutputStream {
    private final CharSequence documentId;
    private final OperationProcessor operationProcessor;
    private final Object context;

    public MultiClusterSessionOutputStream(
            CharSequence documentId,
            OperationProcessor operationProcessor,
            Object context) {
        this.documentId = documentId;
        this.context = context;
        this.operationProcessor = operationProcessor;
    }

    @Override
    public void close() throws IOException {
        Document document = new Document(documentId.toString(), toByteArray(), context);
        operationProcessor.sendDocument(document);
        super.close();
    }
}
