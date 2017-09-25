# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install(FILES src/main/resources/configdefinitions/container-mbus.def RENAME container.jdisc.container-mbus.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/session.def RENAME container.jdisc.config.session.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
        
