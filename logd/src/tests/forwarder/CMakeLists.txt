# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(logd_forwarder_test_app TEST
    SOURCES
    forwarder_test.cpp
    DEPENDS
    logd
)
vespa_add_test(NAME logd_forwarder_test_app COMMAND logd_forwarder_test_app)
