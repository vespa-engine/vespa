// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.cloud.aws;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.io.IOUtils;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.yolean.Exceptions;
import com.yahoo.jdisc.cloud.aws.AwsParameterStore.AwsSettings;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Attempts to validate the AWS Systems Manager Parameter Store settings to see if we can
 * run a working Vespa Cloud Secret Store with them.
 *
 * @author ogronnesby
 */
public class AwsParameterStoreValidationHandler extends LoggingRequestHandler {

    private static final Logger log = Logger.getLogger(AwsParameterStoreValidationHandler.class.getName());

    @Inject
    public AwsParameterStoreValidationHandler(Context ctx) {
        super(ctx);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            if (request.getMethod() == com.yahoo.jdisc.http.HttpRequest.Method.POST) {
                return handlePOST(request);
            }
            return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse handlePOST(HttpRequest request) {
        var json = toSlime(request.getData());
        AwsSettings settings;

        try {
            settings = AwsSettings.fromSlime(json);
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        }

        var response = new Slime();
        var root = response.setObject();
        settings.toSlime(root.setObject("settings"));

        try {
            var parameterName = json.get().field("parameterName").asString();
            var store = new AwsParameterStore(List.of(settings));
            store.getSecret(parameterName);
            root.setString("status", "ok");
        } catch (RuntimeException e) {
            root.setString("status", "error");
            var error = root.setArray("errors").addObject();
            error.setString("type", e.getClass().getSimpleName());
            error.setString("message", Exceptions.toMessageString(e));
        }

        return new SlimeJsonResponse(response);
    }

    private Slime toSlime(InputStream jsonStream) {
        try {
            byte[] jsonBytes = IOUtils.readBytes(jsonStream, 1000 * 1000);
            return SlimeUtils.jsonToSlime(jsonBytes);
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

}