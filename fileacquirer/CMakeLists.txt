# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    vespalog
    vespalib
    config_cloudconfig

    LIBS
    src/vespa/fileacquirer
)

install(FILES src/main/resources/configdefinitions/filedistributorrpc.def RENAME cloud.config.filedistribution.filedistributorrpc.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
