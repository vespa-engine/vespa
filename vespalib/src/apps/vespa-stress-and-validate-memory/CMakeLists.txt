# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_stress-and-validate-memory_app
    SOURCES
    stress_and_validate_memory.cpp
    OUTPUT_NAME vespa-stress-and-validate-memory
    INSTALL bin
    DEPENDS
    vespalib
)
