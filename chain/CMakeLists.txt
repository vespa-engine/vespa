# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install(FILES src/main/resources/configdefinitions/chains.def RENAME container.core.chains.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
