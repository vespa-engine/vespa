# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(messagebus_test_compile-cpp_test_app TEST
    SOURCES
    compile-cpp.cpp
    DEPENDS
    messagebus
)
vespa_add_test(NAME messagebus_test_compile-cpp_test_app NO_VALGRIND COMMAND messagebus_test_compile-cpp_test_app)
