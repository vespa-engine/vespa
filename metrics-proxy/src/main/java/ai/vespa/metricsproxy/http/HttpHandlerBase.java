/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.http;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.Path;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.METHOD_NOT_ALLOWED;
import static com.yahoo.jdisc.Response.Status.NOT_FOUND;
import static com.yahoo.jdisc.Response.Status.OK;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static java.util.logging.Level.WARNING;

/**
 * @author gjoranv
 */
public abstract class HttpHandlerBase extends ThreadedHttpRequestHandler {

    protected HttpHandlerBase(Executor executor) {
        super(executor);
    }

    protected abstract Optional<HttpResponse> doHandle(URI requestUri, Path apiPath, String consumer);

    @Override
    public final HttpResponse handle(HttpRequest request) {
        if (request.getMethod() != GET) return new JsonResponse(METHOD_NOT_ALLOWED, "Only GET is supported");

        Path path = new Path(request.getUri());

        return doHandle(request.getUri(), path, getConsumer(request))
                .orElse(new ErrorResponse(NOT_FOUND, "No content at given path"));
    }

    private String getConsumer(HttpRequest request) {
        return request.getProperty("consumer");
    }

    protected JsonResponse resourceListResponse(URI requestUri, List<String> resources) {
        try {
            return new JsonResponse(OK, resourceList(requestUri, resources));
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
