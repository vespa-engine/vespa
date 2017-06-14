# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespaclient_vespadoclocator_app
    SOURCES
    application.cpp
    locator.cpp
    main.cpp
    OUTPUT_NAME vespa-doclocator-bin
    INSTALL bin
    DEPENDS
)
vespa_add_target_system_dependency(vespaclient_vespadoclocator_app boost boost_program_options-mt-d)
