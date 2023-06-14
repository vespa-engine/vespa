// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.response;

/**
 * This is an <i>optional marker interface</i>.
 * DataLists may implement this to return false to indicate that no data from the list should be returned to clients
 * until it is completed. This is useful in cases where some decision making which may impact the content of the list
 * must be deferred until the list is complete.
 *
 * @author  bratseth
 */
public interface Streamed {

    /**
     * Returns false if the data in this list can not be returned until it is completed.
     * Default: true, meaning eager streaming of the data is permissible.
     */
    boolean isStreamed();

}
