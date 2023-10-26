// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2.response;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.restapi.MessageResponse;

public class DeleteApplicationResponse extends MessageResponse {
    public DeleteApplicationResponse(ApplicationId applicationId) {
        super("Application '" + applicationId + "' deleted");
    }
}
