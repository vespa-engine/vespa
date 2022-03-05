# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fastos_filetest_app TEST
    SOURCES
    filetest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_filetest_app NO_VALGRIND COMMAND fastos_filetest_app)
vespa_add_executable(fastos_thread_stats_test_app TEST
    SOURCES
    thread_stats_test.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_thread_stats_test_app NO_VALGRIND COMMAND fastos_thread_stats_test_app)
vespa_add_executable(fastos_thread_joinwait_test_app TEST
    SOURCES
    thread_joinwait_test.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_thread_joinwait_test_app NO_VALGRIND COMMAND fastos_thread_joinwait_test_app)
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
vespa_add_executable(fastos_typetest_app TEST
    SOURCES
    typetest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_typetest_app NO_VALGRIND COMMAND fastos_typetest_app)
