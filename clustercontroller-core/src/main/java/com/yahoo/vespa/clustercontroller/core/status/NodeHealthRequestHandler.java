// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.status;

import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageResponse;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageServer;

/**
* @author Haakon Humberset
*/
public class NodeHealthRequestHandler implements StatusPageServer.RequestHandler {

    private final RunDataExtractor data;

    public NodeHealthRequestHandler(RunDataExtractor data) {
        this.data = data;
    }

    @Override
    public StatusPageResponse handle(StatusPageServer.HttpRequest request) {
        StatusPageResponse response = new StatusPageResponse();
        response.setContentType("application/json");
        response.writeContent("{\n" +
                          "  \"status\" : {\n" +
                          "    \"code\" : \"up\"\n" +
                          "  },\n" +
                          "  \"config\" : {\n" +
                          "    \"component\" : {\n" +
                          "      \"generation\" : " + data.getConfigGeneration() + "\n" +
                          "    }\n" +
                          "  }\n" +
                          "}");
        return response;
    }

}
