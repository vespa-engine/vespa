// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tracing.h"

#include <opentelemetry/trace/provider.h>

using opentelemetry::trace::Provider;

namespace proton {

[[nodiscard]] std::shared_ptr<opentelemetry::trace::Tracer> get_tracer() {
    auto provider = Provider::GetTracerProvider();
    return provider->GetTracer("proton");
}

} // namespace proton
