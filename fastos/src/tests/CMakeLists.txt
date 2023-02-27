# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fastos_filetest_app TEST
    SOURCES
    filetest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_filetest_app NO_VALGRIND COMMAND fastos_filetest_app)
vespa_add_executable(fastos_typetest_app TEST
    SOURCES
    typetest.cpp
    DEPENDS
    fastos
)
vespa_add_test(NAME fastos_typetest_app NO_VALGRIND COMMAND fastos_typetest_app)
