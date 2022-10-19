# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    feed_reject_helper_test.cpp
    fieldpathupdatetestcase.cpp
    fieldsettest.cpp
    fixed_bucket_spaces_test.cpp
    forcelinktest.cpp
    gid_filter_test.cpp
    globalidtest.cpp
    gtest_runner.cpp
    positiontypetest.cpp
    primitivefieldvaluetest.cpp
    stringtokenizertest.cpp
    structfieldvaluetest.cpp
    testbytebuffer.cpp
    testdocmantest.cpp
    teststringutil.cpp
    testxml.cpp
    weightedsetfieldvaluetest.cpp
    DEPENDS
    document
    GTest::GTest
)

vespa_add_test(
    NAME document_gtest_runner_app
    COMMAND document_gtest_runner_app
)
