# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(juniper_testsuite
    SOURCES
    test.cpp
    DEPENDS
)

vespa_add_executable(juniper_mcandTest_app TEST
    SOURCES
    mcandTest.cpp
    mcandTestApp.cpp
    testenv.cpp
    DEPENDS
    searchsummary
    juniper_testsuite
)
vespa_add_test(NAME juniper_mcandTest_app COMMAND juniper_mcandTest_app)
vespa_add_executable(juniper_queryparserTest_app TEST
    SOURCES
    queryparserTest.cpp
    queryparserTestApp.cpp
    fakerewriter.cpp
    testenv.cpp
    DEPENDS
    searchsummary
    juniper_testsuite
)
vespa_add_test(NAME juniper_queryparserTest_app COMMAND juniper_queryparserTest_app)
vespa_add_executable(juniper_matchobjectTest_app TEST
    SOURCES
    matchobjectTest.cpp
    matchobjectTestApp.cpp
    testenv.cpp
    fakerewriter.cpp
    DEPENDS
    searchsummary
    juniper_testsuite
)
vespa_add_test(NAME juniper_matchobjectTest_app COMMAND juniper_matchobjectTest_app)
vespa_add_executable(juniper_appender_test_app TEST
    SOURCES
    appender_test.cpp
    DEPENDS
    searchsummary
)
vespa_add_test(NAME juniper_appender_test_app COMMAND juniper_appender_test_app)
vespa_add_executable(juniper_queryvisitor_test_app TEST
    SOURCES
    queryvisitor_test.cpp
    DEPENDS
    searchsummary
)
vespa_add_test(NAME juniper_queryvisitor_test_app COMMAND juniper_queryvisitor_test_app)
vespa_add_executable(juniper_auxTest_app TEST
    SOURCES
    auxTest.cpp
    auxTestApp.cpp
    testenv.cpp
    DEPENDS
    searchsummary
    juniper_testsuite
)
vespa_add_test(NAME juniper_auxTest_app COMMAND juniper_auxTest_app)
vespa_add_executable(juniper_SrcTestSuite_app TEST
    SOURCES
    mcandTest.cpp
    queryparserTest.cpp
    fakerewriter.cpp
    SrcTestSuite.cpp
    matchobjectTest.cpp
    auxTest.cpp
    testenv.cpp
    DEPENDS
    searchsummary
    juniper_testsuite
)
vespa_add_test(NAME juniper_SrcTestSuite_app COMMAND juniper_SrcTestSuite_app)
vespa_add_executable(juniper_latintokenizertest_app TEST
    SOURCES
    latintokenizertest.cpp
    DEPENDS
    juniper_testsuite
)
vespa_add_test(NAME juniper_latintokenizertest_app NO_VALGRIND COMMAND juniper_latintokenizertest_app)
