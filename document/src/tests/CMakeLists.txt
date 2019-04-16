# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Runner for unit tests written in gtest.
# NOTE: All new test classes should be added here.
vespa_add_executable(document_gtest_runner_app TEST
    SOURCES
    arrayfieldvaluetest.cpp
    bucketselectortest.cpp
    buckettest.cpp
    documentcalculatortestcase.cpp
    documentidtest.cpp
    documentselectparsertest.cpp
    documenttestcase.cpp
    documenttypetestcase.cpp
    documentupdatetestcase.cpp
    fieldpathupdatetestcase.cpp
    fieldsettest.cpp
    fixed_bucket_spaces_test.cpp
    forcelinktest.cpp
    gid_filter_test.cpp
    globalidtest.cpp
    gtest_runner.cpp
    teststringutil.cpp
    DEPENDS
    document
    gtest
    AFTER
    document_documentconfig
)

vespa_add_test(
    NAME document_gtest_runner_app
    COMMAND document_gtest_runner_app
)

# Runner for unit tests written in CppUnit (DEPRECATED).
vespa_add_executable(document_testrunner_app TEST
    SOURCES
    testbytebuffer.cpp
    stringtokenizertest.cpp
    primitivefieldvaluetest.cpp
    weightedsetfieldvaluetest.cpp
    structfieldvaluetest.cpp
    testdocmantest.cpp
    testxml.cpp
    orderingselectortest.cpp
    testrunner.cpp
    heapdebuggerother.cpp
    positiontypetest.cpp
    urltypetest.cpp
    DEPENDS
    document
    AFTER
    document_documentconfig
)

vespa_add_test(
    NAME document_testrunner_app
    COMMAND document_testrunner_app
)
