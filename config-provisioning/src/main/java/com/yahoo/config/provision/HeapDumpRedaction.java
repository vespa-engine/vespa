// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * Redaction level for Java heap dumps, from the memory-dump element in deployment.xml.
 * Redaction is applied on the node before a heap dump is uploaded to remote storage.
 *
 * @author gjoranv
 */
public enum HeapDumpRedaction {

    /** No redaction (the default) */
    none,

    /** Redact the contents of byte and char arrays (strings, buffers, key material) */
    basic,

    /** Redact all primitive values */
    full

}
