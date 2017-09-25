# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_fat_java_artifact(config-provisioning)
install(FILES src/main/resources/configdefinitions/flavors.def RENAME config.provisioning.flavors.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
