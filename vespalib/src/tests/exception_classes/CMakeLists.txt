# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_exception_classes_test_app TEST
    SOURCES
    exception_classes_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_exception_classes_test_app COMMAND vespalib_exception_classes_test_app)
vespa_add_executable(vespalib_silenceuncaught_test_app TEST
    SOURCES
    silenceuncaught_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_silenceuncaught_test_app COMMAND vespalib_silenceuncaught_test_app)
vespa_add_executable(vespalib_caught_uncaught_app TEST
    SOURCES
    caught_uncaught.cpp
    DEPENDS
    vespalib
)
vespa_add_executable(vespalib_mmap_app TEST
    SOURCES
    mmap.cpp
    DEPENDS
    vespalib
)
