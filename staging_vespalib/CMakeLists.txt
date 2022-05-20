# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
if(NOT CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin")
set(STAGING_VESPALIB_DIRECTIO_TESTDIR src/tests/directio)
set(STAGING_VESPALIB_PROCESS_MEMORY_STATS_TESTDIR src/tests/util/process_memory_stats)
endif()

vespa_define_module(
    DEPENDS
    fastos
    vespalog
    vespalib

    TESTS
    src/tests/array
    src/tests/benchmark
    src/tests/bits
    src/tests/clock
    src/tests/crc
    ${STAGING_VESPALIB_DIRECTIO_TESTDIR}
    src/tests/floatingpointtype
    src/tests/growablebytebuffer
    src/tests/json
    src/tests/memorydatastore
    src/tests/objectdump
    src/tests/objects
    src/tests/objectselection
    src/tests/polymorphicarray
    src/tests/programoptions
    src/tests/rusage
    src/tests/shutdownguard
    src/tests/stllike
    ${STAGING_VESPALIB_PROCESS_MEMORY_STATS_TESTDIR}
    src/tests/xmlserializable

    LIBS
    src/vespa/vespalib
    src/vespa/vespalib/objects
    src/vespa/vespalib/stllike
    src/vespa/vespalib/util
)
