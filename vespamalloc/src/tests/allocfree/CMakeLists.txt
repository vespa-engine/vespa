# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespamalloc_creatingmanythreads_test_app
    SOURCES
    creatingmanythreads.cpp
)
vespa_add_executable(vespamalloc_allocfree_shared_test_app
    SOURCES
    allocfree.cpp
    producerconsumer.cpp
)
vespa_add_executable(vespamalloc_linklist_test_app
    SOURCES
    linklist.cpp
    producerconsumer.cpp
    ../../vespamalloc/malloc/allocchunk.cpp
    ../../vespamalloc/malloc/common.cpp
    $<TARGET_OBJECTS:vespamalloc_util>
    EXTERNAL_DEPENDS
    dl
    ${VESPA_ATOMIC_LIB}
)
vespa_add_test(NAME vespamalloc_allocfree_shared_test_app NO_VALGRIND COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/allocfree_test.sh BENCHMARK
               DEPENDS vespamalloc_allocfree_shared_test_app vespamalloc_linklist_test_app
                       vespamalloc vespamallocd)
