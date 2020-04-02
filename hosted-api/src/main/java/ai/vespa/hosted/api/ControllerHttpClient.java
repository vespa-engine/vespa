// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static ai.vespa.hosted.api.Method.DELETE;
import static ai.vespa.hosted.api.Method.GET;
import static ai.vespa.hosted.api.Method.POST;
import static java.net.http.HttpRequest.BodyPublishers.ofInputStream;
import static java.net.http.HttpResponse.BodyHandlers.ofByteArray;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

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
    public static ControllerHttpClient withSignatureKey(URI endpoint, String privateKey, ApplicationId id) {
        return new SigningControllerHttpClient(endpoint, privateKey, id);
    }

    /** Creates an HTTP client against the given endpoint, which uses the given key to authenticate as the given application. */
    public static ControllerHttpClient withSignatureKey(URI endpoint, Path privateKeyFile, ApplicationId id) {
        return new SigningControllerHttpClient(endpoint, privateKeyFile, id);
    }

    /** Creates an HTTP client against the given endpoint, which uses the given SSL context for authentication. */
    public static ControllerHttpClient withSSLContext(URI endpoint, SSLContext sslContext) {
        return new MutualTlsControllerHttpClient(endpoint, sslContext);
    }

    /** Creates an HTTP client against the given endpoint, which uses the given private key and certificate identity. */
    public static ControllerHttpClient withKeyAndCertificate(URI endpoint, Path privateKeyFile, Path certificateFile) {
        var privateKey = unchecked(() -> KeyUtils.fromPemEncodedPrivateKey(Files.readString(privateKeyFile, UTF_8)));
        var certificates = unchecked(() -> X509CertificateUtils.certificateListFromPem(Files.readString(certificateFile, UTF_8)));

        for (var certificate : certificates)
        if (   Instant.now().isBefore(certificate.getNotBefore().toInstant())
            || Instant.now().isAfter(certificate.getNotAfter().toInstant()))
            throw new IllegalStateException("Certificate at '" + certificateFile + "' is valid between " +
                                            certificate.getNotBefore() + " and " + certificate.getNotAfter() + " — not now.");

        return new MutualTlsControllerHttpClient(endpoint, privateKey, certificates);
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

    /** Sends the given deployment to the given application in the given zone, or throws if this fails. */
    public DeploymentResult deploy(Deployment deployment, ApplicationId id, ZoneId zone) {
        return toDeploymentResult(send(request(HttpRequest.newBuilder(deploymentJobPath(id, zone))
                                                          .timeout(Duration.ofMinutes(20)),
                                               POST,
                                               toDataStream(deployment))));
    }

    /** Deactivates the deployment of the given application in the given zone. */
    public String deactivate(ApplicationId id, ZoneId zone) {
        return toMessage(send(request(HttpRequest.newBuilder(deploymentPath(id, zone))
                                                 .timeout(Duration.ofMinutes(3)),
                                      DELETE)));
    }

    /** Returns the default {@link ZoneId} for the given environment, if any. */
    public ZoneId defaultZone(Environment environment) {
        Inspector rootObject = toInspector(send(request(HttpRequest.newBuilder(defaultRegionPath(environment))
                                                                   .timeout(Duration.ofSeconds(10)),
                                                        GET)));
        return ZoneId.from(environment.value(), rootObject.field("name").asString());
    }

    /** Returns the Vespa version to compile against, for a hosted Vespa application. This is its lowest runtime version. */
    public String compileVersion(ApplicationId id) {
        return toInspector(send(request(HttpRequest.newBuilder(compileVersionPath(id.tenant(), id.application()))
                                                   .timeout(Duration.ofSeconds(20)),
                                        GET)))
                .field("compileVersion").asString();
    }

    /** Returns the test config for functional and verification tests of the indicated Vespa deployment. */
    public TestConfig testConfig(ApplicationId id, ZoneId zone) {
        return TestConfig.fromJson(send(request(HttpRequest.newBuilder(testConfigPath(id, zone))
                                                           .timeout(Duration.ofSeconds(10)),
                                                GET)).body());
    }

    /** Returns the sorted list of log entries after the given after from the deployment job of the given ids. */
    public DeploymentLog deploymentLog(ApplicationId id, ZoneId zone, long run, long after) {
        return toDeploymentLog(send(request(HttpRequest.newBuilder(runPath(id, zone, run, after))
                                                       .timeout(Duration.ofMinutes(2)),
                                            GET)));
    }

    /** Follows the given deployment job until it is done, or this thread is interrupted, at which point the current status is returned. */
    public DeploymentLog followDeploymentUntilDone(ApplicationId id, ZoneId zone, long run,
                                                   Consumer<DeploymentLog.Entry> out) {
        long last = -1;
        DeploymentLog log = null;
        while (true) {
            DeploymentLog update = deploymentLog(id, zone, run, last);
            for (DeploymentLog.Entry entry : update.entries())
                out.accept(entry);
            log = (log == null ? update : log.updatedWith(update));
            last = log.last().orElse(last);

            if ( ! log.isActive())
                break;

            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return log;
    }

    /** Returns the sorted list of log entries from the deployment job of the given ids. */
    public DeploymentLog deploymentLog(ApplicationId id, ZoneId zone, long run) {
        return deploymentLog(id, zone, run, -1);
    }

    /** Returns an authenticated request from the given input. Override this for, e.g., request signing. */
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

    private URI compileVersionPath(TenantName tenant, ApplicationName application) {
        return concatenated(applicationPath(tenant, application), "compile-version");
    }

    private URI instancePath(ApplicationId id) {
        return concatenated(applicationPath(id.tenant(), id.application()), "instance", id.instance().value());
    }

    private URI deploymentPath(ApplicationId id, ZoneId zone) {
        return concatenated(instancePath(id),
                            "environment", zone.environment().value(),
                            "region", zone.region().value());
    }

    private URI deploymentJobPath(ApplicationId id, ZoneId zone) {
        return concatenated(instancePath(id),
                            "deploy", jobNameOf(zone));
    }

    private URI jobPath(ApplicationId id, ZoneId zone) {
        return concatenated(instancePath(id), "job", jobNameOf(zone));
    }

    private URI testConfigPath(ApplicationId id, ZoneId zone) {
        return concatenated(jobPath(id, zone), "test-config");
    }

    private URI runPath(ApplicationId id, ZoneId zone, long run, long after) {
        return withQuery(concatenated(jobPath(id, zone),
                                      "run", Long.toString(run)),
                         "after", Long.toString(after));
    }

    private URI defaultRegionPath(Environment environment) {
        return concatenated(endpoint, "zone", "v1", "environment", environment.value(), "default");
    }

    private static URI concatenated(URI base, String... parts) {
        return base.resolve(Stream.of(parts).map(part -> URLEncoder.encode(part, UTF_8)).collect(joining("/")) + "/");
    }

    private static URI withQuery(URI base, String name, String value) {
        return base.resolve(  "?" + (base.getRawQuery() != null ? base.getRawQuery() + "&" : "")
                            + URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8));
    }

    private static String jobNameOf(ZoneId zone) {
        return (zone.environment().isProduction() ? "production" : zone.environment().value()) + "-" + zone.region().value();
    }

    /** Returns a response with a 2XX status code, with up to 10 attempts, or throws. */
    private HttpResponse<byte[]> send(HttpRequest request) {
        UncheckedIOException thrown = null;
        for (int attempt = 1; attempt <= 10; attempt++) {
            try {
                HttpResponse<byte[]> response = client.send(request, ofByteArray());
                if (response.statusCode() / 100 == 2)
                    return response;

                Inspector rootObject = toSlime(response.body()).get();
                String message = response.request() + " returned code " + response.statusCode() +
                                 (rootObject.field("error-code").valid() ? " (" + rootObject.field("error-code").asString() + ")" : "") +
                                 ": " + rootObject.field("message").asString();

                if (response.statusCode() / 100 == 4)
                    throw new IllegalArgumentException(message);

                throw new IOException(message);

            }
            catch (IOException e) { // Catches the above, and timeout exceptions from the client.
                if (thrown == null)
                    thrown = new UncheckedIOException(e);
                else
                    thrown.addSuppressed(e);

                if (attempt < 10)
                    try {
                        Thread.sleep(100 << attempt);
                    }
                    catch (InterruptedException f) {
                        throw new RuntimeException(f);
                    }
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        throw thrown;
    }

    private static <T> T unchecked(Callable<T> callable) {
        try {
            return callable.call();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Returns a JSON representation of the deployment meta data. */
    private static String metaToJson(Deployment deployment) {
        Slime slime = new Slime();
        Cursor rootObject = slime.setObject();
        deployment.version().ifPresent(version -> rootObject.setString("vespaVersion", version));
        rootObject.setBool("deployDirectly", true);
        return toJson(slime);
    }

    /** Returns a JSON representation of the submission meta data. */
    private static String metaToJson(Submission submission) {
        Slime slime = new Slime();
        Cursor rootObject = slime.setObject();
        submission.repository().ifPresent(repository -> rootObject.setString("repository", repository));
        submission.branch().ifPresent(branch -> rootObject.setString("branch", branch));
        submission.commit().ifPresent(commit -> rootObject.setString("commit", commit));
        submission.sourceUrl().ifPresent(url -> rootObject.setString("sourceUrl", url));
        submission.authorEmail().ifPresent(email -> rootObject.setString("authorEmail", email));
        submission.projectId().ifPresent(projectId -> rootObject.setLong("projectId", projectId));
        return toJson(slime);
    }

    /** Returns a multi part data stream with meta data and, if contained in the deployment, an application package. */
    private static MultiPartStreamer toDataStream(Deployment deployment) {
        MultiPartStreamer streamer = new MultiPartStreamer();
        streamer.addJson("deployOptions", metaToJson(deployment));
        streamer.addFile("applicationZip", deployment.applicationZip());
        return streamer;
    }

    /** Returns the response body as a String, or throws if the status code is non-2XX. */
    private static String asString(HttpResponse<byte[]> response) {
        return new String(response.body(), UTF_8);
    }

    /** Returns an {@link Inspector} for the assumed JSON formatted response. */
    private static Inspector toInspector(HttpResponse<byte[]> response) {
        return toSlime(response.body()).get();
    }

    /** Returns the "message" element contained in the JSON formatted response. */
    private static String toMessage(HttpResponse<byte[]> response) {
        return toInspector(response).field("message").asString();
    }

    private static DeploymentResult toDeploymentResult(HttpResponse<byte[]> response) {
        Inspector rootObject = toInspector(response);
        return new DeploymentResult(rootObject.field("message").asString(),
                                    rootObject.field("run").asLong());
    }

    private static DeploymentLog toDeploymentLog(HttpResponse<byte[]> response) {
        Inspector rootObject = toInspector(response);
        List<DeploymentLog.Entry> entries = new ArrayList<>();
        rootObject.field("log").traverse((ObjectTraverser) (step, entryArray) ->
                entryArray.traverse((ArrayTraverser) (___, entryObject) -> {
                    entries.add(new DeploymentLog.Entry(Instant.ofEpochMilli(entryObject.field("at").asLong()),
                                                        DeploymentLog.Level.of(entryObject.field("type").asString()),
                                                        entryObject.field("message").asString(),
                                                        "copyVespaLogs".equals(step)));
                }));
        return new DeploymentLog(entries,
                                 rootObject.field("active").asBool(),
                                 valueOf(rootObject.field("status").asString()),
                                 rootObject.field("lastId").valid() ? OptionalLong.of(rootObject.field("lastId").asLong())
                                                                    : OptionalLong.empty());
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

        private SigningControllerHttpClient(URI endpoint, String privateKey, ApplicationId id) {
            super(endpoint, HttpClient.newBuilder());
            this.signer = new RequestSigner(privateKey, id.serializedForm());
        }

        private SigningControllerHttpClient(URI endpoint, Path privateKeyFile, ApplicationId id) {
            this(endpoint, unchecked(() -> Files.readString(privateKeyFile, UTF_8)), id);
        }

        @Override
        protected HttpRequest request(HttpRequest.Builder request, Method method, Supplier<InputStream> data) {
            return signer.signed(request, method, data);
        }

    }


    /** Client that uses a given key / certificate identity to authenticate to the remote controller. */
    private static class MutualTlsControllerHttpClient extends ControllerHttpClient {

        private MutualTlsControllerHttpClient(URI endpoint, SSLContext sslContext) {
            super(endpoint, HttpClient.newBuilder().sslContext(sslContext));
        }

        private MutualTlsControllerHttpClient(URI endpoint, PrivateKey privateKey, List<X509Certificate> certs) {
            this(endpoint, new SslContextBuilder().withKeyStore(privateKey, certs).build());
        }

    }


    private static DeploymentLog.Status valueOf(String status) {
        switch (status) {
            case "running":                    return DeploymentLog.Status.running;
            case "aborted":                    return DeploymentLog.Status.aborted;
            case "error":                      return DeploymentLog.Status.error;
            case "testFailure":                return DeploymentLog.Status.testFailure;
            case "outOfCapacity":              return DeploymentLog.Status.outOfCapacity;
            case "installationFailed":         return DeploymentLog.Status.installationFailed;
            case "deploymentFailed":           return DeploymentLog.Status.deploymentFailed;
            case "endpointCertificateTimeout": return DeploymentLog.Status.endpointCertificateTimeout;
            case "success":                    return DeploymentLog.Status.success;
            default: throw new IllegalArgumentException("Unexpected status '" + status + "'");
        }
    }

}
