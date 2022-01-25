// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

/**
 * Something which can be connected to a network when ready to receive incoming requests.
 *
 * @author jonmv
 */
public interface Connectable {

    void connect();
    void disconnect();

}
