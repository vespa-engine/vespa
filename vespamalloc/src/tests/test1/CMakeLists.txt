# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespamalloc_testatomic_app TEST
    SOURCES
    testatomic.cpp
    ../../vespamalloc/malloc/mmappool.cpp
    ../../vespamalloc/malloc/common.cpp
    DEPENDS
    vespamalloc_util
    EXTERNAL_DEPENDS
    ${VESPA_ATOMIC_LIB}
    dl
)
vespa_add_test(NAME vespamalloc_testatomic_app NO_VALGRIND COMMAND vespamalloc_testatomic_app)

vespa_add_executable(vespamalloc_new_test_app TEST
    SOURCES
    new_test.cpp
    EXTERNAL_DEPENDS
    dl
)
vespa_add_test(NAME vespamalloc_new_test_app NO_VALGRIND COMMAND vespamalloc_new_test_app)

vespa_add_executable(vespamalloc_new_test_with_vespamalloc_app TEST
    SOURCES
    new_test.cpp
    DEPENDS
    vespamalloc
)
vespa_add_test(NAME vespamalloc_new_test_with_vespamalloc_app NO_VALGRIND COMMAND vespamalloc_new_test_with_vespamalloc_app)

vespa_add_executable(vespamalloc_new_test_with_vespamallocd_app TEST
    SOURCES
    new_test.cpp
    DEPENDS
    vespamallocd
)
vespa_add_test(NAME vespamalloc_new_test_with_vespamallocd_app NO_VALGRIND COMMAND vespamalloc_new_test_with_vespamallocd_app)
