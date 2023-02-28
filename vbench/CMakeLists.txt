# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespalib

    LIBS
    src/vbench
    src/vbench/core
    src/vbench/http
    src/vbench/test
    src/vbench/vbench

    APPS
    src/apps/dumpurl
    src/apps/vbench

    TESTS
    src/tests/app_dumpurl
    src/tests/app_vbench
    src/tests/benchmark_headers
    src/tests/dispatcher
    src/tests/dropped_tagger
    src/tests/handler_thread
    src/tests/hex_number
    src/tests/http_client
    src/tests/http_connection
    src/tests/http_connection_pool
    src/tests/input_file_reader
    src/tests/latency_analyzer
    src/tests/line_reader
    src/tests/qps_analyzer
    src/tests/qps_tagger
    src/tests/request_dumper
    src/tests/request_generator
    src/tests/request_sink
    src/tests/server_spec
    src/tests/server_tagger
    src/tests/socket
    src/tests/taint
    src/tests/time_queue
    src/tests/timer
)
