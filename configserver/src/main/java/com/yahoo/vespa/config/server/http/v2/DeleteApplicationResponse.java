package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.restapi.MessageResponse;

public class DeleteApplicationResponse extends MessageResponse {
    public DeleteApplicationResponse(ApplicationId applicationId) {
        super("Application '" + applicationId + "' deleted");
    }
}
