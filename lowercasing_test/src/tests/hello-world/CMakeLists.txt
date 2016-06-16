# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(lowercasing_test_hello-world-local_app TEST
    SOURCES
    hello-world-local.cpp
    DEPENDS
    lowercasing_test_hello-world-lib
)
vespa_add_test(NAME lowercasing_test_hello-world-local_app NO_VALGRIND COMMAND sh hello-world_test.sh)
