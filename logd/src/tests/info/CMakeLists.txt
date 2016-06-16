# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(logd_info_test_app TEST
    SOURCES
    info.cpp
    DEPENDS
)
vespa_add_test(NAME logd_info_test_app NO_VALGRIND COMMAND logd_info_test_app)
