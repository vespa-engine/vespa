# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespamalloc_testatomic_app TEST
    SOURCES
    testatomic.cpp
    DEPENDS
)
vespa_add_test(NAME vespamalloc_testatomic_app NO_VALGRIND COMMAND vespamalloc_testatomic_app)
