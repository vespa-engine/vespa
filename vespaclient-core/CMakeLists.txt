# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install(FILES src/main/resources/configdefinitions/feeder.def RENAME vespaclient.config.feeder.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/spooler.def RENAME vespa.config.content.spooler.spooler.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
