# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fastlib_httpheaderparsertest_app TEST
    SOURCES
    httpheaderparsertest.cpp
    DEPENDS
    fastlib_fast
)
vespa_add_test(NAME fastlib_httpheaderparsertest_app NO_VALGRIND COMMAND fastlib_httpheaderparsertest_app ${CMAKE_CURRENT_SOURCE_DIR}/headers.txt)
