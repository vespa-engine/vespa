// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.ServerResponseException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

public interface GatewayConnection {

    /** Returns the time this connected over the network, or null if not connected yet */
    Instant connectionTime();

    /** Returns the last time poll was called on this, or null if never */
    Instant lastPollTime();

    InputStream write(List<Document> docs) throws ServerResponseException, IOException;

    /** Returns any operation results that are ready now */
    InputStream poll() throws ServerResponseException, IOException;

    /** Attempt to drain all outstanding operations, even if this leads to blocking */
    InputStream drain() throws ServerResponseException, IOException;

    boolean connect();

    Endpoint getEndpoint();

    void handshake() throws ServerResponseException, IOException;

    void close();

}
