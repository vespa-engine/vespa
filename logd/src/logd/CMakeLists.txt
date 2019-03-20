# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(logd STATIC
    SOURCES
    conf.cpp
    conn.cpp
    forward.cpp
    metrics.cpp
    state.cpp
    watch.cpp

    DEPENDS
)
vespa_generate_config(logd ../main/resources/configdefinitions/logd.def)
install_config_definition(../main/resources/configdefinitions/logd.def cloud.config.log.logd.def)
