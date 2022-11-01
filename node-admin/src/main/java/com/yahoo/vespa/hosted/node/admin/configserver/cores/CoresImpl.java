// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.cores;

import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;
import com.yahoo.vespa.hosted.node.admin.configserver.StandardConfigServerResponse;
import com.yahoo.vespa.hosted.node.admin.configserver.cores.bindings.ReportCoreDumpRequest;

/**
 * @author hakonhall
 */
public class CoresImpl implements Cores {
    private final ConfigServerApi configServerApi;

    public CoresImpl(ConfigServerApi configServerApi) {
        this.configServerApi = configServerApi;
    }

    @Override
    public void report(HostName hostname, String id, CoreDumpMetadata metadata) {
        var request = new ReportCoreDumpRequest().fillFrom(metadata);
        String uriPath = "/cores/v1/report/" + hostname.value() + "/" + id;
        configServerApi.post(uriPath, request, StandardConfigServerResponse.class)
                       .throwOnError("Failed to report core dump at " + metadata.coredumpPath());
    }
}
