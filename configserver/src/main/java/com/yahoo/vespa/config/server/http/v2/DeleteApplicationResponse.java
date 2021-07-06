package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.http.JSONResponse;

class DeleteApplicationResponse extends JSONResponse {
    DeleteApplicationResponse(int status, ApplicationId applicationId) {
        super(status);
        object.setString("message", "Application '" + applicationId + "' deleted");
    }
}
