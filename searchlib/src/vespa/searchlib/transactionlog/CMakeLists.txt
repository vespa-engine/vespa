# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_transactionlog OBJECT
    SOURCES
    chunks.cpp
    common.cpp
    domain.cpp
    domainpart.cpp
    ichunk.cpp
    nosyncproxy.cpp
    session.cpp
    trans_log_server_explorer.cpp
    translogclient.cpp
    translogserver.cpp
    translogserverapp.cpp
    DEPENDS
    searchlib_sconfig
    AFTER
    searchlib_common
)
