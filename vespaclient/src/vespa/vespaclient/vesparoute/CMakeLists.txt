# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespaclient_vesparoute_app
    SOURCES
    application.cpp
    main.cpp
    mynetwork.cpp
    params.cpp
    OUTPUT_NAME vespa-route-bin
    INSTALL bin
    DEPENDS
)
