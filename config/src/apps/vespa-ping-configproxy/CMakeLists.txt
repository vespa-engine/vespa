# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(config_pingproxy_app
    SOURCES
    pingproxy.cpp
    OUTPUT_NAME vespa-ping-configproxy
    INSTALL bin
    DEPENDS
)
