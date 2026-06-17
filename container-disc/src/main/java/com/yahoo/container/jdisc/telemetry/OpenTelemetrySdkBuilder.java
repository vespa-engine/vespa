// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.telemetry;

import ai.vespa.telemetry.TelemetryConfig;
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
 * Builds the real OpenTelemetry SDK (OTLP/HTTP exporter, batching, parent-based ratio sampling, W3C trace
 * context propagation) from {@link TelemetryConfig}.
 *
 * <p>Kept separate from {@link OpenTelemetryProvider} on purpose: the JVM loads this class only when the
 * provider takes its enabled branch, so the OTel SDK classes are needed on the classpath only when tracing
 * is actually enabled. When disabled, the provider references only the OTel API.</p>
 *
 * @author onur
 */
class OpenTelemetrySdkBuilder {

    private OpenTelemetrySdkBuilder() {}

    /** Returns the configured SDK as an {@link OpenTelemetry}; the concrete type also implements AutoCloseable. */
    static OpenTelemetry build(TelemetryConfig config) {
        AttributesBuilder attributes = Attributes.builder();
        config.resourceAttribute().forEach(attributes::put);
        Resource resource = Resource.getDefault().merge(Resource.create(attributes.build()));

        return OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .setResource(resource)
                        .setSampler(Sampler.parentBased(Sampler.traceIdRatioBased(config.samplingRatio())))
                        .addSpanProcessor(BatchSpanProcessor.builder(
                                OtlpHttpSpanExporter.builder().setEndpoint(config.endpoint()).build()).build())
                        .build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
    }

}
