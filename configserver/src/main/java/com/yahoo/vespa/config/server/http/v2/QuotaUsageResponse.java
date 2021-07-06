package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.jdisc.Response;
import com.yahoo.vespa.config.server.http.JSONResponse;

class QuotaUsageResponse extends JSONResponse {
    QuotaUsageResponse(double usageRate) {
        super(Response.Status.OK);
        object.setDouble("rate", usageRate);
    }
}
