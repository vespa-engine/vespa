# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalog_bufferedlogtest_app TEST
    SOURCES
    bufferedlogtest.cpp
    bufferedlogtest.logger1.cpp
    bufferedlogtest.logger2.cpp
    DEPENDS
    vespalog
)
vespa_add_test(NAME vespalog_bufferedlogtest_app NO_VALGRIND COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/bufferedlogtest_test.sh
               DEPENDS vespalog_bufferedlogtest_app)
vespa_add_executable(vespalog_bufferedlogskiptest_app TEST
    SOURCES
    bufferedlogskiptest.cpp
    DEPENDS
    vespalog
)
vespa_add_test(NAME vespalog_bufferedlogskiptest_app NO_VALGRIND COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/bufferedlogskiptest_test.sh
               DEPENDS vespalog_bufferedlogskiptest_app)
vespa_add_executable(vespalog_rejectfiltertest_app TEST
    SOURCES
    rejectfiltertest.cpp
    DEPENDS
    vespalog
)
vespa_add_test(NAME vespalog_rejectfiltertest_app NO_VALGRIND COMMAND vespalog_rejectfiltertest_app)
file(COPY "${CMAKE_CURRENT_SOURCE_DIR}/" DESTINATION ${CMAKE_CURRENT_BINARY_DIR} FILES_MATCHING PATTERN "*.log")
