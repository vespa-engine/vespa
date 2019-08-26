/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.http;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.List;
import java.util.logging.Logger;

import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.OK;
import static java.util.logging.Level.WARNING;

/**
 * @author gjoranv
 */
public class RestApiUtil {
    private static Logger log = Logger.getLogger(RestApiUtil.class.getName());


    static JsonResponse resourceListResponse(URI requestUri, List<String> resources) {
        try {
            return new JsonResponse(OK, RestApiUtil.resourceList(requestUri, resources));
        } catch (JSONException e) {
            log.log(WARNING, "Bad JSON construction in generated resource list for " + requestUri.getPath(), e);
            return new ErrorResponse(INTERNAL_SERVER_ERROR,
                                     "An error occurred when generating the list of api resources.");
        }
    }

    // TODO: Use jackson with a "Resources" class instead of JSONObject
    private static String resourceList(URI requestUri, List<String> resources) throws JSONException {
        int port = requestUri.getPort();
        String host = requestUri.getHost();
        StringBuilder base = new StringBuilder("http://");
        base.append(host);
        if (port >= 0) {
            base.append(":").append(port);
        }
        String uriBase = base.toString();
        JSONArray linkList = new JSONArray();
        for (String api : resources) {
            JSONObject resource = new JSONObject();
            resource.put("url", uriBase + api);
            linkList.put(resource);
        }
        return new JSONObject().put("resources", linkList).toString(4);
    }

}
