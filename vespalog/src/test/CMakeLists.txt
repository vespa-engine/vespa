# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalog_bufferedlogtest_app
    SOURCES
    bufferedlogtest.cpp
    bufferedlogtest.logger1.cpp
    bufferedlogtest.logger2.cpp
    DEPENDS
    vespalog
)
vespa_add_test(NAME vespalog_bufferedlogtest_app NO_VALGRIND COMMAND sh bufferedlogtest_test.sh)
vespa_add_executable(vespalog_bufferedlogskiptest_app
    SOURCES
    bufferedlogskiptest.cpp
    DEPENDS
    vespalog
)
vespa_add_test(NAME vespalog_bufferedlogskiptest_app NO_VALGRIND COMMAND sh bufferedlogskiptest_test.sh) 
vespa_add_executable(vespalog_rejectfiltertest_app
    SOURCES
    rejectfiltertest.cpp
    DEPENDS
    vespalog
)
vespa_add_test(NAME vespalog_rejectfiltertest_app NO_VALGRIND COMMAND vespalog_rejectfiltertest_app)
