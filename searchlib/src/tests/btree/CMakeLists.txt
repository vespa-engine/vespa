# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_scanspeed_app
    SOURCES
    scanspeed.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_scanspeed_app COMMAND vespalib_scanspeed_app BENCHMARK)
