# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fastos_processtest_app TEST
    SOURCES
    processtest.cpp
    DEPENDS
    fastos
)
vespa_add_executable(fastos_filetest_app TEST
    SOURCES
    filetest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_filetest_app NO_VALGRIND COMMAND fastos_filetest_app)
vespa_add_executable(fastos_sockettest_app TEST
    SOURCES
    sockettest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_sockettest_app NO_VALGRIND COMMAND fastos_sockettest_app)
vespa_add_executable(fastos_thread_stats_test_app TEST
    SOURCES
    thread_stats_test.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_thread_stats_test_app NO_VALGRIND COMMAND fastos_thread_stats_test_app)
vespa_add_executable(fastos_thread_sleep_test_app TEST
    SOURCES
    thread_sleep_test.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_thread_sleep_test_app NO_VALGRIND COMMAND fastos_thread_sleep_test_app)
vespa_add_executable(fastos_thread_mutex_test_app TEST
    SOURCES
    thread_mutex_test.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_thread_mutex_test_app NO_VALGRIND COMMAND fastos_thread_mutex_test_app)
vespa_add_executable(fastos_thread_joinwait_test_app TEST
    SOURCES
    thread_joinwait_test.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_thread_joinwait_test_app NO_VALGRIND COMMAND fastos_thread_joinwait_test_app)
vespa_add_executable(fastos_thread_bounce_test_app TEST
    SOURCES
    thread_bounce_test.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_thread_bounce_test_app NO_VALGRIND COMMAND fastos_thread_bounce_test_app)
vespa_add_executable(fastos_threadtest_app TEST
    SOURCES
    threadtest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_threadtest_app NO_VALGRIND COMMAND fastos_threadtest_app)
vespa_add_executable(fastos_backtracetest_app TEST
    SOURCES
    backtracetest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_backtracetest_app NO_VALGRIND COMMAND fastos_backtracetest_app)
vespa_add_executable(fastos_timetest_app TEST
    SOURCES
    timetest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_timetest_app NO_VALGRIND COMMAND fastos_timetest_app)
vespa_add_executable(fastos_typetest_app TEST
    SOURCES
    typetest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_typetest_app NO_VALGRIND COMMAND fastos_typetest_app)
vespa_add_executable(fastos_usecputest_app TEST
    SOURCES
    usecputest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_usecputest_app NO_VALGRIND COMMAND fastos_usecputest_app)
