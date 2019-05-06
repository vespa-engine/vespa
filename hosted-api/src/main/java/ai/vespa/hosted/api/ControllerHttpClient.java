// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static ai.vespa.hosted.api.Method.POST;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Talks to a remote controller over HTTP.
 *
 * Uses request signing with a public/private key pair to authenticate with the controller.
 *
 * @author jonmv
 */
public class ControllerHttpClient {

    private final ApplicationId id;
    private final RequestSigner signer;
    private final URI endpoint;
    private final HttpClient client;

    /** Creates a HTTP client against the given endpoint, which uses the given key to authenticate as the given application. */
    public ControllerHttpClient(URI endpoint, String privateKey, ApplicationId id) {
        this.id = id;
        this.signer = new RequestSigner(privateKey, id.serializedForm());
        this.endpoint = endpoint.resolve("/");
        this.client = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(5))
                                .build();
    }

    /** Sends submission to the remote controller and returns the version of the accepted package, or throws if this fails. */
    public String submit(Submission submission) {
        HttpRequest request = signer.signed(HttpRequest.newBuilder(applicationPath(id.tenant(), id.application()).resolve("submit"))
                                                       .timeout(Duration.ofMinutes(30)),
                                            POST,
                                            new MultiPartStreamer().addJson("submitOptions", metoToJson(submission))
                                                                   .addFile("applicationZip", submission.applicationZip())
                                                                   .addFile("applicationTestZip", submission.applicationTestZip()));
        try {
            return toMessage(client.send(request, HttpResponse.BodyHandlers.ofByteArray()));
        }
        catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private URI apiPath() {
        return concatenated(endpoint, "application", "v4");
    }

    private URI tenantPath(TenantName tenant) {
        return concatenated(apiPath(), "tenant", tenant.value());
    }

    private URI applicationPath(TenantName tenant, ApplicationName application) {
        return concatenated(tenantPath(tenant), "application", application.value());
    }

    private URI instancePath(ApplicationId id) {
        return concatenated(applicationPath(id.tenant(), id.application()), "instance", id.instance().value());
    }

    private static URI concatenated(URI base, String... parts) {
        return base.resolve(String.join("/", parts) + "/");
    }

    /** Returns a JSON representation of the submission meta data. */
    private static String metaToJson(Submission submission) {
        try {
            Slime slime = new Slime();
            Cursor rootObject = slime.setObject();
            rootObject.setString("repository", submission.repository());
            rootObject.setString("branch", submission.branch());
            rootObject.setString("commit", submission.commit());
            rootObject.setString("authorEmail", submission.authorEmail());
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            new JsonFormat(true).encode(buffer, slime);
            return buffer.toString(UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns the "message" element contained in the JSON formatted response, if 2XX status code, or throws otherwise. */
    private static String toMessage(HttpResponse<byte[]> response) {
        Inspector rootObject = toSlime(response.body()).get();
        if (response.statusCode() / 100 == 2)
            return rootObject.field("message").asString();

        else {
            throw new RuntimeException(response.request() + " returned code " + response.statusCode() +
                                       " (" + rootObject.field("error-code").asString() + "): " +
                                       rootObject.field("message").asString());
        }
    }

    private static Slime toSlime(byte[] data) {
        return new JsonDecoder().decode(new Slime(), data);
    }

}
