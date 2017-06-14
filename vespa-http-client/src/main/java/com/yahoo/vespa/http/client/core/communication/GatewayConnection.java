// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.ServerResponseException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface GatewayConnection {
    InputStream writeOperations(List<Document> docs) throws ServerResponseException, IOException;

    InputStream drain() throws ServerResponseException, IOException;

    boolean connect();

    Endpoint getEndpoint();

    void handshake() throws ServerResponseException, IOException;

    void close();
}
