# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vbench_http OBJECT
    SOURCES
    benchmark_headers.cpp
    hex_number.cpp
    http_client.cpp
    http_connection.cpp
    http_connection_pool.cpp
    http_result_handler.cpp
    server_spec.cpp
    DEPENDS
)
