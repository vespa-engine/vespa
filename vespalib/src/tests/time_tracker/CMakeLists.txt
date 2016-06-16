# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_time_tracker_test_app TEST
    SOURCES
    time_tracker_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_time_tracker_test_app COMMAND vespalib_time_tracker_test_app)
