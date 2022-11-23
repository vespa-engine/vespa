// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

/**
 * Interface for getting a connection given a node id.
 *
 * @author balderersheim
 */
public interface RpcConnectionPool {
    Client.NodeConnection getConnection(int nodeId);
}
