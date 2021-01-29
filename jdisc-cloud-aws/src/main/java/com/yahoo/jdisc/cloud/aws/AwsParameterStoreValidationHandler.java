// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.cloud.aws;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.io.IOUtils;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;

/**
 * Attempts to validate the AWS Systems Manager Parameter Store settings to see if we can
 * run a working Vespa Cloud Secret Store with them.
 *
 * @author ogronnesby
 */
public class AwsParameterStoreValidationHandler extends LoggingRequestHandler {

    private final VespaAwsCredentialsProvider credentialsProvider;

    @Inject
    public AwsParameterStoreValidationHandler(Context ctx) {
        this(ctx, new VespaAwsCredentialsProvider());
    }


    public AwsParameterStoreValidationHandler(Context ctx, VespaAwsCredentialsProvider credentialsProvider) {
        super(ctx);
        this.credentialsProvider = credentialsProvider;
    }


    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET: return handleGET();
                case POST: return handlePOST(request);
                default: return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            }
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse handleGET() {
        return new SlimeJsonResponse();
    }

    private HttpResponse handlePOST(HttpRequest request) {
        var json = toSlime(request.getData());
        var settings = AwsSettings.fromSlime(json);

        var response = new Slime();
        var root = response.get();
        settings.toSlime(response.get().setObject("settings"));

        try {
            var store = new AwsParameterStore(this.credentialsProvider, settings.role, settings.externalId);
            store.getSecret("vespa-secret");
            root.setString("status", "ok");
        } catch (RuntimeException e) {
            root.setString("status", "error");
            var error = root.setArray("errors").addObject();
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

    private static class AwsSettings {
        String name;
        String role;
        String awsId;
        String externalId;

        AwsSettings(String name, String role, String awsId, String externalId) {
            this.name = name;
            this.role = role;
            this.awsId = awsId;
            this.externalId = externalId;
        }

        static AwsSettings fromSlime(Slime slime) {
            var json = slime.get();
            return new AwsSettings(
                    json.field("name").asString(),
                    json.field("role").asString(),
                    json.field("awsId").asString(),
                    json.field("externalId").asString()
            );
        }

        void toSlime(Cursor slime) {
            slime.setString("name", name);
            slime.setString("role", role);
            slime.setString("awsId", awsId);
            slime.setString("externalId", "*****");
        }
    }
}
