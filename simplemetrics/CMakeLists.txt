# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_fat_java_artifact(simplemetrics)

install(FILES src/main/resources/configdefinitions/manager.def RENAME metrics.manager.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)

