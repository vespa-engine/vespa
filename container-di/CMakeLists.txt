# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install(FILES src/main/resources/configdefinitions/bundles.def RENAME container.bundles.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/components.def RENAME container.components.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/jersey-bundles.def RENAME container.di.config.jersey-bundles.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/jersey-injection.def RENAME container.di.config.jersey-injection.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
        
