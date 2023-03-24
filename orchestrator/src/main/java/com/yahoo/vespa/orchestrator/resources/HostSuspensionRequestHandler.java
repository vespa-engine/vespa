// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.jdisc.Response;
import com.yahoo.restapi.JacksonJsonResponse;
import com.yahoo.restapi.RestApi;
import com.yahoo.restapi.RestApiException;
import com.yahoo.restapi.RestApiRequestHandler;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.BatchHostNameNotFoundException;
import com.yahoo.vespa.orchestrator.BatchInternalErrorException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.policy.BatchHostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchOperationResult;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author hakonhall
 * @author bjorncs
 */
public class HostSuspensionRequestHandler extends RestApiRequestHandler<HostSuspensionRequestHandler> {

    private static final Logger log = Logger.getLogger(HostSuspensionRequestHandler.class.getName());

    private final Orchestrator orchestrator;

    @Inject
    public HostSuspensionRequestHandler(ThreadedHttpRequestHandler.Context context, Orchestrator orchestrator) {
        super(context, HostSuspensionRequestHandler::createRestApiDefinition);
        this.orchestrator = orchestrator;
    }

    private static RestApi createRestApiDefinition(HostSuspensionRequestHandler self) {
        return RestApi.builder()
                .addRoute(RestApi.route("/orchestrator/v1/suspensions/hosts/{hostname}")
                    .put(self::suspendAll))
                .registerJacksonResponseEntity(BatchOperationResult.class)
                .build();
    }

    private BatchOperationResult suspendAll(RestApi.RequestContext context) {
        String parentHostnameString = context.pathParameters().getStringOrThrow("hostname");
        List<String> hostnamesAsStrings = context.queryParameters().getStringList("hostname");

        HostName parentHostname = new HostName(parentHostnameString);
        List<HostName> hostnames = hostnamesAsStrings.stream().map(HostName::new).toList();
        try {
            orchestrator.suspendAll(parentHostname, hostnames);
        } catch (BatchHostStateChangeDeniedException | UncheckedTimeoutException e) {
            log.log(Level.FINE, e, () -> "Failed to suspend nodes " + hostnames + " with parent host " + parentHostname);
            throw createRestApiException(e.getMessage(), Response.Status.CONFLICT, e);
        } catch (BatchHostNameNotFoundException e) {
            log.log(Level.FINE, e, () -> "Failed to suspend nodes " + hostnames + " with parent host " + parentHostname);
            // Note that we're returning BAD_REQUEST instead of NOT_FOUND because the resource identified
            // by the URL path was found. It's one of the hostnames in the request it failed to find.
            throw createRestApiException(e.getMessage(), Response.Status.BAD_REQUEST, e);
        } catch (BatchInternalErrorException e) {
            log.log(Level.FINE, e, () -> "Failed to suspend nodes " + hostnames + " with parent host " + parentHostname);
            throw createRestApiException(e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR, e);
        }
        log.log(Level.FINE, () -> "Suspended " + hostnames + " with parent " + parentHostname);
        return BatchOperationResult.successResult();
    }

    private RestApiException createRestApiException(String errorMessage, int statusCode, Throwable cause) {
        return new RestApiException(
                new JacksonJsonResponse<>(statusCode, new BatchOperationResult(errorMessage), true),
                errorMessage,
                cause);
    }
}
