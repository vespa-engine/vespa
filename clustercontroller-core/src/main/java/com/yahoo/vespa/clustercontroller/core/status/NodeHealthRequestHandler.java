// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.status;

import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageResponse;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageServer;

/**
* @author <a href="mailto:humbe@yahoo-inc.com">Haakon Humberset</a>
*/
public class NodeHealthRequestHandler implements StatusPageServer.RequestHandler {
    private final RunDataExtractor data;

    public NodeHealthRequestHandler(RunDataExtractor data) {
        this.data = data;
    }

    @Override
    public StatusPageResponse handle(StatusPageServer.HttpRequest request) {
        StatusPageResponse response = new StatusPageResponse();
        StringBuilder content = new StringBuilder();
        response.setContentType("application/json");
        content.append(
            "{\n" +
            "  \"status\" : {\n" +
            "    \"code\" : \"up\"\n" +
            "  },\n" +
            "  \"config\" : {\n" +
            "    \"component\" : {\n" +
            "      \"generation\" : " + data.getConfigGeneration() + "\n" +
            "    }\n" +
            "  }\n" +
            "}"
        );
        response.writeContent(content.toString());
        return response;
    }
}
