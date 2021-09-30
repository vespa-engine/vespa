# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespa-tensor-conformance
    SOURCES
    generate.cpp
    tensor_conformance.cpp
    INSTALL bin
    DEPENDS
    vespaeval
)
