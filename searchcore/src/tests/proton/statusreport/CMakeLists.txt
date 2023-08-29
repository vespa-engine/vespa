# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_statusreport_test_app TEST
    SOURCES
    statusreport_test.cpp
    DEPENDS
    searchcore_pcommon
)
vespa_add_test(NAME searchcore_statusreport_test_app COMMAND searchcore_statusreport_test_app)
