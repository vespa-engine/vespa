// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.deployment;

import com.yahoo.component.Version;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponses;
import com.yahoo.yolean.Exceptions;

/**
 * This handler implements the /cli/v1/ API. The API allows Vespa CLI to retrieve information about the system, without
 * authorization. One example of such information is the minimum Vespa CLI version supported by our APIs.
 *
 * @author mpolden
 */
public class CliApiHandler extends ThreadedHttpRequestHandler {

    /**
     * The minimum version of Vespa CLI which is considered compatible with our APIs. If a version of Vespa CLI below
     * this version tries to use our APIs, Vespa CLI will print a warning instructing the user to upgrade.
     */
    private static final Version MIN_CLI_VERSION = Version.fromString("7.547.18");

    public CliApiHandler(Context context) {
        super(context);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET: return get(request);
                default: return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            }
        }
        catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        }
        catch (RuntimeException e) {
            return ErrorResponses.logThrowing(request, log, e);
        }
    }

    private HttpResponse get(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/cli/v1/")) return root();
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse root() {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("minVersion", MIN_CLI_VERSION.toFullString());
        return new SlimeJsonResponse(slime);
    }

}
