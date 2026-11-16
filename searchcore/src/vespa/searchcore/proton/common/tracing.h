// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <opentelemetry/trace/tracer.h>

namespace proton {

[[nodiscard]] std::shared_ptr<opentelemetry::trace::Tracer> get_tracer();

}
