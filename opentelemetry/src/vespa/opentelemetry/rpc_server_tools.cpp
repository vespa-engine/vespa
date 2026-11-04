// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpc_server_tools.h"

#include <opentelemetry/context/propagation/global_propagator.h>
#include <opentelemetry/context/runtime_context.h>
#include <opentelemetry/trace/provider.h>
#include <opentelemetry/trace/span_metadata.h>

using opentelemetry::context::Context;
using opentelemetry::context::RuntimeContext;
using opentelemetry::context::propagation::GlobalTextMapPropagator;
using opentelemetry::context::propagation::TextMapCarrier;
using opentelemetry::trace::GetSpan;
using opentelemetry::trace::Provider;
using opentelemetry::trace::Span;
using opentelemetry::trace::SpanKind;
using opentelemetry::trace::StartSpanOptions;
using opentelemetry::trace::Tracer;

namespace vespa_opentelemetry {

Context RpcServerTools::make_request_context(const TextMapCarrier& carrier) {
    auto propagator = GlobalTextMapPropagator::GetGlobalPropagator();
    auto current_context = RuntimeContext::GetCurrent();
    return propagator->Extract(carrier, current_context);
}

std::optional<Context> RpcServerTools::make_optional_request_context(const ProtoTelemetryServerCarrier& carrier) {
    std::optional<Context> request_context;
    if (!carrier.empty()) {
        request_context.emplace(make_request_context(carrier));
    }
    return request_context;
}

std::shared_ptr<Span> RpcServerTools::make_server_span(const Context& request_context, Tracer& tracer,
                                                       const std::string& span_name) {
    StartSpanOptions options;
    options.kind = SpanKind::kServer;
    options.parent = GetSpan(request_context)->GetContext();
    return tracer.StartSpan(span_name, options);
}

} // namespace vespa_opentelemetry
