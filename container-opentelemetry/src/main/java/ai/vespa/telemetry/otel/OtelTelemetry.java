// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.telemetry.otel;

import ai.vespa.telemetry.TelemetryConfig;
import ai.vespa.telemetry.api.NoopTelemetry;
import ai.vespa.telemetry.api.Telemetry;
import ai.vespa.telemetry.api.trace.ScopedTracer;
import com.yahoo.security.X509SslContext;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.TransportSecurityUtils;
import com.yahoo.vespa.defaults.Defaults;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * The OpenTelemetry-backed {@link Telemetry}: owns one {@link OpenTelemetrySdk} (OTLP/HTTP exporter,
 * batching, parent-based ratio sampling, W3C trace context propagation) built from {@link TelemetryConfig}.
 *
 * <p>Lives in this module, next to the embedded SDK, so that the SDK packages need not be OSGi-exported.
 * {@code container-disc}'s TelemetryProvider references this class only inside its enabled branch, so the
 * JVM loads it — and therefore the SDK classes — only when telemetry is actually enabled.</p>
 *
 * <p>The exporter targets the local host's Alloy OTLP receiver. Its endpoint is host-specific and not known
 * at deploy time, so it is resolved at startup from the hostname host-admin writes into the container
 * (see {@code ContainerDataGenerator}); the fixed receiver port matches host-admin's {@code GrafanaAlloyTask}.</p>
 *
 * @author onur
 */
public final class OtelTelemetry implements Telemetry, AutoCloseable {

    private static final Logger log = Logger.getLogger(OtelTelemetry.class.getName());

    /** Port the host's Alloy OTLP receiver listens on (HTTP). Must match host-admin's GrafanaAlloyTask. */
    private static final int ALLOY_OTLP_HTTP_PORT = 4318;

    private final OpenTelemetrySdk sdk;

    private OtelTelemetry(OpenTelemetrySdk sdk) { this.sdk = sdk; }

    /**
     * Called by TelemetryProvider only when {@code config.enabled()}. Never construct otherwise.
     *
     * <p>Returns {@link NoopTelemetry#INSTANCE} if the local host's Alloy endpoint cannot be resolved
     * (e.g. the hostname file is absent) or if no system TLS context is available. The no-op is not
     * AutoCloseable, so deconstructing a degraded instance does nothing.</p>
     */
    public static Telemetry create(TelemetryConfig config) {
        Path hostnameFile = Path.of(Defaults.getDefaults().underVespaHome(config.endpointHostnameFile()));
        String endpoint = resolveEndpoint(hostnameFile);
        if (endpoint == null) {
            log.warning("OpenTelemetry tracing is enabled but the host hostname file " + hostnameFile +
                    " is missing or empty; tracing disabled (no-op).");
            return NoopTelemetry.INSTANCE;
        }

        // The hop to the host's Alloy receiver is mutual TLS: present the container's own system TLS
        // identity and trust the host's cert via the same Vespa Athenz CA. The SSLContext is backed by
        // auto-reloading key/trust managers, so SIA cert rotation is picked up without rebuilding the exporter.
        // Server hostname verification is intentionally off: the container's system TLS config sets
        // hostnameValidationDisabled, and reusing the system trust manager (PeerAuthorizerTrustManager) disables
        // endpoint identification for client connections. So the endpoint host (the parent-host name written by
        // host-admin) need not match the host cert's SAN -- the same posture as the Alloy->gateway hop.
        //
        // Resolved AFTER the endpoint gate above, deliberately: getSystemTlsContext() caches a SystemTlsContext
        // in a JVM-lifetime static and starts an hourly crypto-material reloader thread, so it must not run when
        // there is no endpoint to export to.
        TlsContext tlsContext = TransportSecurityUtils.getSystemTlsContext().orElse(null);
        if (tlsContext == null) {
            log.warning("OpenTelemetry tracing is enabled but no system TLS context is available " +
                    "(system TLS not configured); tracing disabled (no-op).");
            return NoopTelemetry.INSTANCE;
        }
        X509SslContext ssl = tlsContext.sslContext();

        AttributesBuilder attributes = Attributes.builder();
        config.resourceAttribute().forEach(attributes::put);
        Resource resource = Resource.getDefault().merge(Resource.create(attributes.build()));

        return new OtelTelemetry(OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .setResource(resource)
                        .setSampler(Sampler.parentBased(Sampler.traceIdRatioBased(config.samplingRatio())))
                        .addSpanProcessor(BatchSpanProcessor.builder(
                                OtlpHttpSpanExporter.builder()
                                        .setEndpoint(endpoint)
                                        .setSslContext(ssl.context(), ssl.trustManager())
                                        .build()).build())
                        .build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build());
    }

    /**
     * Builds the local host's Alloy OTLP endpoint from the hostname host-admin wrote into the container,
     * or {@code null} if the file is missing, unreadable or empty.
     *
     * <p>The endpoint must be the full OTLP/HTTP traces URL including the {@code /v1/traces} path:
     * {@code OtlpHttpSpanExporter.setEndpoint} uses it verbatim (it does not append the signal path), so a
     * bare {@code https://host:4318} would POST to {@code /} and the receiver answers 404.</p>
     */
    static String resolveEndpoint(Path hostnameFile) {
        try {
            String hostname = Files.readString(hostnameFile).trim();
            if (hostname.isEmpty()) return null;
            return "https://" + hostname + ":" + ALLOY_OTLP_HTTP_PORT + "/v1/traces";
        } catch (IOException e) {
            return null;
        }
    }

    @Override public ScopedTracer tracer(String scope) { return new ScopedTracer(sdk.getTracer(scope)); }
    @Override public TextMapPropagator textMapPropagator() { return sdk.getPropagators().getTextMapPropagator(); }
    @Override public void close() { sdk.close(); }
}
