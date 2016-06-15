# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(filedistribution_filedistributorrpcconfig STATIC
    SOURCES
    DEPENDS
)
vespa_generate_config(filedistribution_filedistributorrpcconfig filedistributorrpc.def)
install(FILES filedistributorrpc.def DESTINATION var/db/vespa/config_server/serverdb/classes)
