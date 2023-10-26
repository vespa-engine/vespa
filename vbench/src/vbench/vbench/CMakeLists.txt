# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vbench_vbench_vbench OBJECT
    SOURCES
    analyzer.cpp
    dropped_tagger.cpp
    generator.cpp
    ignore_before.cpp
    latency_analyzer.cpp
    native_factory.cpp
    qps_analyzer.cpp
    qps_tagger.cpp
    request.cpp
    request_dumper.cpp
    request_generator.cpp
    request_scheduler.cpp
    request_sink.cpp
    server_tagger.cpp
    tagger.cpp
    vbench.cpp
    worker.cpp
    DEPENDS
)
