// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpc_request_telemetry_context.h"

#include <opentelemetry/context/propagation/global_propagator.h>
#include <opentelemetry/context/runtime_context.h>
#include <opentelemetry/trace/provider.h>
#include <opentelemetry/trace/span_metadata.h>

using opentelemetry::context::Context;
using opentelemetry::context::RuntimeContext;
using opentelemetry::context::propagation::GlobalTextMapPropagator;
using opentelemetry::context::propagation::TextMapCarrier;
using opentelemetry::trace::Provider;
using opentelemetry::trace::Span;
using opentelemetry::trace::SpanKind;
using opentelemetry::trace::StartSpanOptions;
using opentelemetry::trace::Tracer;

namespace vespa_opentelemetry {

RpcRequestTelemetryContext::RpcRequestTelemetryContext() : _span() {
}

RpcRequestTelemetryContext::~RpcRequestTelemetryContext() {
    clear();
}

void RpcRequestTelemetryContext::setup(const ProtoTelemetryServerCarrier& carrier, std::string_view tracer_name,
                                       std::string_view span_name) {
    if (!carrier.empty()) {
        auto             propagator = GlobalTextMapPropagator::GetGlobalPropagator();
        auto             current_context = RuntimeContext::GetCurrent();
        auto             parent_context = propagator->Extract(carrier, current_context);
        auto             provider = Provider::GetTracerProvider();
        auto             tracer = provider->GetTracer(tracer_name);
        StartSpanOptions options;
        options.kind = SpanKind::kServer;
        options.parent = parent_context;
        _span = tracer->StartSpan(span_name, options);
    }
}

void RpcRequestTelemetryContext::clear() {
    if (_span) {
        _span->End();
        _span.reset();
    }
}

} // namespace vespa_opentelemetry
