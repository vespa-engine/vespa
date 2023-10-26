# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(config_configproxy-cmd_app
    SOURCES
    main.cpp
    methods.cpp
    proxycmd.cpp
    OUTPUT_NAME vespa-configproxy-cmd-bin
    INSTALL bin
    DEPENDS
)
