// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static ai.vespa.hosted.api.Method.POST;
import static java.net.http.HttpRequest.BodyPublishers.ofByteArray;
import static java.net.http.HttpRequest.BodyPublishers.ofInputStream;
import static java.net.http.HttpResponse.BodyHandlers.ofByteArray;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Talks to a remote controller over HTTP. Subclasses are responsible for adding authentication to the requests.
 *
 * @author jonmv
 */
public abstract class ControllerHttpClient {

    private final HttpClient client;
    private final URI endpoint;

    /** Creates an HTTP client against the given endpoint, using the given HTTP client builder to create a client. */
    protected ControllerHttpClient(URI endpoint, HttpClient.Builder client) {
        this.endpoint = endpoint.resolve("/");
        this.client = client.connectTimeout(Duration.ofSeconds(5))
                            .version(HttpClient.Version.HTTP_1_1)
                            .build();
    }

    /** Creates an HTTP client against the given endpoint, which uses the given key to authenticate as the given application. */
    public static ControllerHttpClient withSignatureKey(URI endpoint, Path privateKeyFile, ApplicationId id) {
        return new SigningControllerHttpClient(endpoint, privateKeyFile, id);
    }

    /** Creates an HTTP client against the given endpoint, which uses the given private key and certificate identity. */
    public static ControllerHttpClient withKeyAndCertificate(URI endpoint, Path privateKeyFile, Path certificateFile) {
        return new MutualTlsControllerHttpClient(endpoint, privateKeyFile, certificateFile);
    }

    /** Sends the given submission to the remote controller and returns the version of the accepted package, or throws if this fails. */
    public String submit(Submission submission, TenantName tenant, ApplicationName application) {
        return toMessage(send(request(HttpRequest.newBuilder(applicationPath(tenant, application).resolve("submit"))
                                                 .timeout(Duration.ofMinutes(30)),
                                      POST,
                                      new MultiPartStreamer().addJson("submitOptions", metaToJson(submission))
                                                             .addFile("applicationZip", submission.applicationZip())
                                                             .addFile("applicationTestZip", submission.applicationTestZip()))));
    }

    protected HttpRequest request(HttpRequest.Builder request, Method method, Supplier<InputStream> data) {
        return request.method(method.name(), ofInputStream(data)).build();
    }

    private HttpRequest request(HttpRequest.Builder request, Method method) {
        return request(request, method, InputStream::nullInputStream);
    }

    private HttpRequest request(HttpRequest.Builder request, Method method, byte[] data) {
        return request(request, method, () -> new ByteArrayInputStream(data));
    }

    private HttpRequest request(HttpRequest.Builder request, Method method, MultiPartStreamer data) {
        return request(request.setHeader("Content-Type", data.contentType()), method, data::data);
    }

    private URI applicationApiPath() {
        return concatenated(endpoint, "application", "v4");
    }

    private URI tenantPath(TenantName tenant) {
        return concatenated(applicationApiPath(), "tenant", tenant.value());
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

    private HttpResponse<byte[]> send(HttpRequest request) {
        return unchecked(() -> client.send(request, ofByteArray()));
    }

    private static <T> T unchecked(Callable<T> callable) {
        try {
            return callable.call();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Returns a JSON representation of the submission meta data. */
    private static String metaToJson(Submission submission) {
        Slime slime = new Slime();
        Cursor rootObject = slime.setObject();
        rootObject.setString("repository", submission.repository());
        rootObject.setString("branch", submission.branch());
        rootObject.setString("commit", submission.commit());
        rootObject.setString("authorEmail", submission.authorEmail());
        return toJson(slime);
    }

    /** Returns an {@link Inspector} for the assumed JSON formatted response, or throws if the status code is non-2XX. */
    private static Inspector toInspector(HttpResponse<byte[]> response) {
        Inspector rootObject = toSlime(response.body()).get();
        if (response.statusCode() / 100 != 2)
            throw new RuntimeException(response.request() + " returned code " + response.statusCode() +
                                       " (" + rootObject.field("error-code").asString() + "): " +
                                       rootObject.field("message").asString());

        return rootObject;
    }

    /** Returns the "message" element contained in the JSON formatted response, if 2XX status code, or throws otherwise. */
    private static String toMessage(HttpResponse<byte[]> response) {
        return toInspector(response).field("message").asString();
    }

    private static Slime toSlime(byte[] data) {
        return new JsonDecoder().decode(new Slime(), data);
    }

    private static String toJson(Slime slime) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            new JsonFormat(true).encode(buffer, slime);
            return buffer.toString(UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    /** Client that signs requests with a private key whose public part is assigned to an application in the remote controller. */
    private static class SigningControllerHttpClient extends ControllerHttpClient {

        private final RequestSigner signer;

        private SigningControllerHttpClient(URI endpoint, Path privateKeyFile, ApplicationId id) {
            super(endpoint, HttpClient.newBuilder());
            this.signer = new RequestSigner(unchecked(() -> Files.readString(privateKeyFile, UTF_8)), id.serializedForm());
        }

        @Override
        protected HttpRequest request(HttpRequest.Builder request, Method method, Supplier<InputStream> data) {
            return signer.signed(request, method, data);
        }

    }


    /** Client that uses a given key / certificate identity to authenticate to the remote controller. */
    private static class MutualTlsControllerHttpClient extends ControllerHttpClient {

        private MutualTlsControllerHttpClient(URI endpoint, Path privateKeyFile, Path certificateFile) {
            super(endpoint,
                  HttpClient.newBuilder().sslContext(new SslContextBuilder().withKeyStore(privateKeyFile, certificateFile).build()));
        }

    }

}
