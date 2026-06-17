// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.telemetry;

import ai.vespa.telemetry.TelemetryConfig;
import com.yahoo.component.annotation.Inject;
import com.yahoo.container.di.componentgraph.Provider;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;

/**
 * Provides the container's {@link OpenTelemetry} instance as an injectable component.
 *
 * <p>Disabled by default: when {@code telemetry.enabled=false} this hands out {@link OpenTelemetry#noop()},
 * which constructs no SDK, starts no exporter threads, opens no connections and produces no telemetry.
 * The real SDK (OTLP/HTTP exporter, batching, sampling) is built only when explicitly enabled, so the
 * component is safe to ship disabled and roll out gradually via config.</p>
 *
 * @author onur
 */
public class OpenTelemetryProvider implements Provider<OpenTelemetry> {

    private final OpenTelemetrySdk sdk;            // null when disabled
    private final OpenTelemetry openTelemetry;

    @Inject
    public OpenTelemetryProvider(TelemetryConfig config) {
        if (! config.enabled()) {
            this.sdk = null;
            this.openTelemetry = OpenTelemetry.noop();
            return;
        }

        AttributesBuilder attributes = Attributes.builder();
        config.resourceAttribute().forEach(attributes::put);
        Resource resource = Resource.getDefault().merge(Resource.create(attributes.build()));

        this.sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .setResource(resource)
                        .setSampler(Sampler.parentBased(Sampler.traceIdRatioBased(config.samplingRatio())))
                        .addSpanProcessor(BatchSpanProcessor.builder(
                                OtlpHttpSpanExporter.builder().setEndpoint(config.endpoint()).build()).build())
                        .build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        this.openTelemetry = sdk;
    }

    @Override
    public OpenTelemetry get() { return openTelemetry; }

    @Override
    public void deconstruct() {
        if (sdk != null) sdk.close();   // flush queued spans + stop exporter threads on reconfiguration
    }

}
