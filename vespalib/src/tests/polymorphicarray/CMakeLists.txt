# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_polymorphicarray_test_app TEST
    SOURCES
    polymorphicarray_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_polymorphicarray_test_app COMMAND vespalib_polymorphicarray_test_app)
