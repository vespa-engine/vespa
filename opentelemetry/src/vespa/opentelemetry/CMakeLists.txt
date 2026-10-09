# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespa_opentelemetry
    SOURCES
    opentelemetry_tracer_providers.cpp
    INSTALL lib64
    DEPENDS
    opentelemetry-cpp::api
    opentelemetry-cpp::trace
    opentelemetry-cpp::ostream_span_exporter
    opentelemetry-cpp::in_memory_span_exporter
    opentelemetry-cpp::otlp_grpc_exporter
)
