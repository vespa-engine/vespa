# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
if(NOT CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin" AND
   NOT DEFINED VESPA_USE_SANITIZER)
add_compile_options(-fvisibility=hidden)
add_definitions(-DPARANOID_LEVEL=0)

vespa_define_module(
    TEST_DEPENDS
    fastos
    vespalib
    vespalog

    TESTS
    src/tests
    src/tests/allocfree
    src/tests/doubledelete
    src/tests/overwrite
    src/tests/stacktrace
    src/tests/test1
    src/tests/test2
    src/tests/thread

    LIBS
    src/vespamalloc
    src/vespamalloc/malloc
    src/vespamalloc/util
)

else()
install(DIRECTORY DESTINATION lib64/vespa)
endif()
install(FILES etc/vespamalloc.conf PERMISSIONS OWNER_READ GROUP_READ WORLD_READ DESTINATION etc)
