# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespaclient_spoolmaster_app
    SOURCES
    main.cpp
    application.cpp
    OUTPUT_NAME vespa-spoolmaster
    INSTALL bin
    DEPENDS
)
