# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fastos_processtest_app
    SOURCES
    processtest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_processtest_app NO_VALGRIND COMMAND fastos_processtest_app fastos_processtest_app fastos_usecputest_app)
vespa_add_executable(fastos_prefetchtest_app
    SOURCES
    prefetchtest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_prefetchtest_app NO_VALGRIND COMMAND fastos_prefetchtest_app)
vespa_add_executable(fastos_filetest_app
    SOURCES
    filetest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_filetest_app NO_VALGRIND COMMAND fastos_filetest_app)
vespa_add_executable(fastos_sockettest_app
    SOURCES
    sockettest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_sockettest_app NO_VALGRIND COMMAND fastos_sockettest_app)
vespa_add_executable(fastos_threadtest_app
    SOURCES
    threadtest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_threadtest_app NO_VALGRIND COMMAND fastos_threadtest_app)
vespa_add_executable(fastos_backtracetest_app
    SOURCES
    backtracetest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_backtracetest_app NO_VALGRIND COMMAND fastos_backtracetest_app)
vespa_add_executable(fastos_timetest_app
    SOURCES
    timetest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_timetest_app NO_VALGRIND COMMAND fastos_timetest_app)
vespa_add_executable(fastos_typetest_app
    SOURCES
    typetest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_typetest_app NO_VALGRIND COMMAND fastos_typetest_app)
vespa_add_executable(fastos_usecputest_app
    SOURCES
    usecputest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_usecputest_app NO_VALGRIND COMMAND fastos_usecputest_app)
