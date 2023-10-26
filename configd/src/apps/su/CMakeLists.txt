# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(configd_vespa-run-as-vespa-user_app
    SOURCES
    main.cpp
    OUTPUT_NAME vespa-run-as-vespa-user
    INSTALL bin
    DEPENDS
)
