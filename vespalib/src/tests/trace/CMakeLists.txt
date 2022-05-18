# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_trace_test_app TEST
    SOURCES
    trace.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_trace_test_app COMMAND vespalib_trace_test_app)

vespa_add_executable(vespalib_trace_serialization_test_app TEST
    SOURCES
    trace_serialization.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_trace_serialization_test_app COMMAND vespalib_trace_serialization_test_app)
