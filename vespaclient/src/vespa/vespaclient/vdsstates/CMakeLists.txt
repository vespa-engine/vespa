# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespaclient_vdsgetnodestate_app
    SOURCES
    statesapp.cpp
    OUTPUT_NAME vdsgetnodestate-bin
    DEPENDS
    vespaclient_clusterlist
)
