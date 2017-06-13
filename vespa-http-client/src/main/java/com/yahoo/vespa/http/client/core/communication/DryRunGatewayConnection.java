// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.ErrorCode;
import com.yahoo.vespa.http.client.core.OperationStatus;
import com.yahoo.vespa.http.client.core.ServerResponseException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Dummy implementation.
 *
 * @author dybis
 */
public class DryRunGatewayConnection implements GatewayConnection {

    private final Endpoint endpoint;

    public DryRunGatewayConnection(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public InputStream writeOperations(List<Document> docs) throws ServerResponseException, IOException {
        StringBuilder result = new StringBuilder();
        for (Document doc : docs) {
            OperationStatus operationStatus = new OperationStatus("ok", doc.getOperationId(), ErrorCode.OK, false, "");
            result.append(operationStatus.render());
        }
        return new ByteArrayInputStream(result.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public InputStream drain() throws ServerResponseException, IOException {
        return writeOperations(new ArrayList<Document>());
    }

    @Override
    public boolean connect() {
        return true;
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public void handshake() throws ServerResponseException, IOException { }

    @Override
    public void close() { }
}
