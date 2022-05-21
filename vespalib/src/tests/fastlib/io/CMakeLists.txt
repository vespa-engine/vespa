# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fastlib_bufferedfiletest_app TEST
    SOURCES
    bufferedfiletest.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME fastlib_bufferedfiletest_app COMMAND fastlib_bufferedfiletest_app)
