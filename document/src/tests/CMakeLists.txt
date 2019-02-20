# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Runner for unit tests written in gtest.
# NOTE: All new test classes should be added here.
vespa_add_executable(document_gtest_runner_app TEST
    SOURCES
    documentidtest.cpp
    documentupdatetestcase.cpp
    gtest_runner.cpp
    DEPENDS
    document
    gtest
    AFTER
    document_documentconfig
)

vespa_add_test(
    NAME document_gtest_runner_app
    COMMAND document_gtest_runner_app
    DEPENDS document_gtest_runner_app
)

# Runner for unit tests written in CppUnit (DEPRECATED).
vespa_add_executable(document_testrunner_app TEST
    SOURCES
    teststringutil.cpp
    testbytebuffer.cpp
    stringtokenizertest.cpp
    documentcalculatortestcase.cpp
    buckettest.cpp
    globalidtest.cpp
    documenttypetestcase.cpp
    primitivefieldvaluetest.cpp
    arrayfieldvaluetest.cpp
    weightedsetfieldvaluetest.cpp
    structfieldvaluetest.cpp
    documenttestcase.cpp
    testdocmantest.cpp
    fieldpathupdatetestcase.cpp
    documentselectparsertest.cpp
    bucketselectortest.cpp
    testxml.cpp
    forcelinktest.cpp
    orderingselectortest.cpp
    testrunner.cpp
    heapdebuggerother.cpp
    positiontypetest.cpp
    urltypetest.cpp
    fieldsettest.cpp
    gid_filter_test.cpp
    fixed_bucket_spaces_test.cpp
    DEPENDS
    document
    AFTER
    document_documentconfig
)

# TODO: Test with a larger chunk size to parallelize test suite runs 
vespa_add_test(
    NAME document_testrunner_app
    COMMAND python ${PROJECT_SOURCE_DIR}/cppunit-parallelize.py --chunks 1 $<TARGET_FILE:document_testrunner_app>
    DEPENDS document_testrunner_app
)
