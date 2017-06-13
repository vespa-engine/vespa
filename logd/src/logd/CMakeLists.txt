# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(logd STATIC
    SOURCES
    watch.cpp
    conn.cpp
    conf.cpp
    forward.cpp
    service.cpp
    cmdbuf.cpp
    perform.cpp
    sigterm.cpp
    DEPENDS
)
vespa_generate_config(logd ../main/resources/configdefinitions/logd.def)
install(FILES ../main/resources/configdefinitions/logd.def DESTINATION var/db/vespa/config_server/serverdb/classes)
