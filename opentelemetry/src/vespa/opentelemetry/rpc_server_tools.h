// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "proto_telemetry_server_carrier.h"

#include <opentelemetry/context/context.h>
#include <opentelemetry/trace/tracer.h>

#include <memory>
#include <optional>

namespace vespa_opentelemetry {

/*
 * Functions used by an rpc server to conditionally add spans below the client span on the client.
 */

class RpcServerTools {
public:
    using Context = opentelemetry::context::Context;
    using Span = opentelemetry::trace::Span;
    using TextMapCarrier = opentelemetry::context::propagation::TextMapCarrier;
    using Tracer = opentelemetry::trace::Tracer;
    [[nodiscard]] static Context make_request_context(const TextMapCarrier& carrier);
    [[nodiscard]] static std::optional<Context>
    make_optional_request_context(const ProtoTelemetryServerCarrier& carrier);
    [[nodiscard]] static std::shared_ptr<Span> make_server_span(const Context& request_context, Tracer& tracer,
                                                                const std::string& span_name);
};

} // namespace vespa_opentelemetry
