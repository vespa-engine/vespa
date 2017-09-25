# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_fat_java_artifact(container-search-and-docproc)

install(FILES src/main/resources/configdefinitions/application-userdata.def RENAME container.handler.observability.application-userdata.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
