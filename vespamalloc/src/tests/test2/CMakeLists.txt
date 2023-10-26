# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespamalloc_testgraph_app TEST
    SOURCES
    testgraph.cpp
    ../../vespamalloc/util/stream.cpp
    ../../vespamalloc/util/traceutil.cpp
    ../../vespamalloc/util/callstack.cpp
    EXTERNAL_DEPENDS
    dl
)
vespa_add_test(NAME vespamalloc_testgraph_app NO_VALGRIND COMMAND vespamalloc_testgraph_app)
