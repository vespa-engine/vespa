// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.cores;

import com.yahoo.config.provision.HostName;

/**
 * @author hakonhall
 */
public interface Cores {
    /**
     * @param hostname Hostname of the node that produced the core.
     * @param id       The ID (aka UUID aka docid) of the core.
     * @param metadata Core dump metadata.
     */
    void report(HostName hostname, String id, CoreDumpMetadata metadata);
}
