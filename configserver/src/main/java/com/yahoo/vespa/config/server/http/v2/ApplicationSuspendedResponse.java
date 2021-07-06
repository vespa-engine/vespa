package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.jdisc.Response;
import com.yahoo.vespa.config.server.http.JSONResponse;

class ApplicationSuspendedResponse extends JSONResponse {
    ApplicationSuspendedResponse(boolean suspended) {
        super(Response.Status.OK);
        object.setBool("suspended", suspended);
    }
}
