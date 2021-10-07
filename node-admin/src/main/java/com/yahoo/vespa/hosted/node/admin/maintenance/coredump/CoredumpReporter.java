// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.coredump;

/**
 * @author freva
 */
public interface CoredumpReporter {

    /** Report a coredump with a given ID and given metadata */
    void reportCoredump(String id, String metadata);
}
